package models.dao;

import static models.ExchangeRate.HISTORY_SIZE;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import models.ExchangeRate;
import play.Logger;

import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.cql.CqlStatementResult;
import com.netflix.astyanax.ddl.ColumnDefinition;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;

/**
 * This is the Data Access Layer which is used to read/store the ExchangeRates to/from the DB.  
 */
public class ExchangeRateDAOImpl implements ExchangeRateDAO {
	
	private static final String COLUMN_FAMILY_NAME = "exchange_rates";

	/*
	 * This is CQL statement could be improved.
	 * 
	 * I expected the keys in the table schema to be sorted so I had planned to read rows across a range of keys (where each key is a date).
	 * I later learned that keys are not sorted and that Cassandra Partitioning is a subject matter I need to read up on so, rather than delay, I 
	 * altered the CQL to use "WHERE date IN (x, y, z, etc)".  Unfortunate but I will read up on this in the coming days!  
	 */
	private static final String CQL_SELECT_CURRENCY = "SELECT date, @CURRENCY@ FROM exchange_rates WHERE date IN (@DATES@);";

	private static ColumnFamily<Long, String> COLUMN_FAMILY = new ColumnFamily<Long, String>(COLUMN_FAMILY_NAME,
			LongSerializer.get(),
			StringSerializer.get());
	
	private static Keyspace KEYSPACE;

	/**
	 * This DAL uses Astyanax to communicate with Cassandra  
	 * @param cassandraContext
	 */
	public ExchangeRateDAOImpl (AstyanaxContext<Keyspace> cassandraContext) {
		KEYSPACE = cassandraContext.getEntity();
	}
	
	
	/**
	 * Public interface used to read ExchangeRates from Cassandra for a specified Currency
	 */
	@Override
	public ExchangeRate getRates(String currency) {
		final ExchangeRate ret = new ExchangeRate(currency);
		
		try {
			final String cql = prepareCQL(currency);
			
			final OperationResult<CqlStatementResult> result = KEYSPACE.prepareCqlStatement().withCql(cql).execute();
			
			final Iterator<Row<Long, String>> rows = result.getResult().getRows(COLUMN_FAMILY).iterator();
			
			while (rows.hasNext()) {
				final Row<Long, String> row = rows.next();
				ret.addRate(row.getKey(), readRate(row, currency));
			}
		} catch (Exception e) {
			Logger.error("Error reading currency " + currency + " : " + e.getMessage());
		}

		return ret;
	}


	/*
	 * Convenience method used to extract an individual rate from a Column 
	 */
	private static Float readRate(final Row<Long, String> row, String currency) {
		try {
			final Column<String> columnByName = row.getColumns().getColumnByName(currency);
			if (columnByName != null) {
				return columnByName.getFloatValue();
			} else {
				return null;
			}
		} catch (Exception e) {
			Logger.error("Error retrieving rate for "+ currency + ": " + e.getMessage());
			return null;
		}
	}


	/**
	 * Public interface used to insert all ExchangeRates into the DB  
	 */
	@Override
	public void insert(Iterator<ExchangeRate> exchangeRates) {
		final MutationBatch mb = KEYSPACE.prepareMutationBatch();
		
		try {
			final Set<String> knownCurrencies = readKnownCurrencies();
			
			while (exchangeRates.hasNext()) {
				final ExchangeRate exchangeRate = exchangeRates.next();
				
				if (isNewCurrency(exchangeRate, knownCurrencies)) {
					addCurrencyColumn(exchangeRate);
					knownCurrencies.add(exchangeRate.currency);
				}
				
				addToBatch(mb, exchangeRate);
			}
			mb.execute();
		} catch (ConnectionException e) {
			Logger.info("Error inserting " + e.getMessage());
		}
	}


	/*
	 * Convenience method used during insertion of all ExchangeRates into the DB
	 */
	private static void addToBatch(final MutationBatch mb, final ExchangeRate e) {
		final Iterator<Long> dates = e.rates.keySet().iterator();
		while (dates.hasNext()) {
			final Long date = dates.next();
			mb.withRow(COLUMN_FAMILY, date).putColumn(e.currency, e.rates.get(date));	
		}
	}

	/*
	 * Add a new currency to the DB by adding a new Column to the ColumnFamily.  The name of the column 
	 * is the name of the currency (e.g. USD, AUD etc)      
	 */
	private static void addCurrencyColumn(final ExchangeRate exchangeRate) throws ConnectionException {
		KEYSPACE.prepareCqlStatement()
				.withCql("ALTER TABLE exchange_rates ADD " + exchangeRate.currency + " float;")
				.execute();
		
		Logger.debug("Added new currency to ColumnFamily : " + exchangeRate.currency);
	}


	/*
	 * Convenience method to ask the Set of known currencies if it already contains the currency of the specified ExchangeRate
	 */
	private static boolean isNewCurrency(final ExchangeRate exchangeRate, final Set<String> knownCurrencies) {
		return !knownCurrencies.contains(exchangeRate.currency);
	}


	/**
	 * Read all Column names from the ColumnFamily.  Each column name represents the name of a Currency.
	 */
	public Set<String> readKnownCurrencies() throws ConnectionException {
		final Set<String> ret = new HashSet<String>();
		
		final List<ColumnDefinition> defs = KEYSPACE.describeKeyspace().getColumnFamily(COLUMN_FAMILY_NAME).getColumnDefinitionList();
		
		for (ColumnDefinition def : defs) {
			ret.add(def.getName());
		}
		
		return ret;
	}

	/*
	 * This is CQL statement could be improved.
	 * 
	 * I expected the keys in the table schema to be sorted so I had planned to read rows across a range of keys (where each key is a date).
	 * I later learned that keys are not sorted and that Cassandra Partitioning is a subject matter I need to read up on so, rather than delay, I 
	 * altered the CQL to use "WHERE date IN (x, y, z, etc)".  Unfortunate but I will read up on this in the coming days!  
	 */
	private static String prepareCQL(String currency) {
		final StringBuilder sb = new StringBuilder();

		final Calendar c = Calendar.getInstance();
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.getTimeInMillis();

		for (int i=HISTORY_SIZE; i>0; i--) {
			sb.append(c.getTimeInMillis());
			sb.append(',');
			c.add(Calendar.DATE, -1);
		}
		
		sb.deleteCharAt(sb.length()-1);
		
		return CQL_SELECT_CURRENCY.replaceFirst("@CURRENCY@", currency).replaceFirst("@DATES@", sb.toString());
	}
	
}
