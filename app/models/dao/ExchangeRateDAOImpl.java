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

public class ExchangeRateDAOImpl implements ExchangeRateDAO {
	
	private static final String COLUMN_FAMILY_NAME = "exchange_rates";

	private static final String CQL_SELECT_CURRENCY = "SELECT date, @CURRENCY@ FROM exchange_rates WHERE date IN (@DATES@);";

	private static ColumnFamily<Long, String> COLUMN_FAMILY = new ColumnFamily<Long, String>(COLUMN_FAMILY_NAME,
			LongSerializer.get(),
			StringSerializer.get());
	
	
	private static Keyspace KEYSPACE;
	
	
	public ExchangeRateDAOImpl (AstyanaxContext<Keyspace> cassandraContext) {
		KEYSPACE = cassandraContext.getEntity();
	}
	
	
	@Override
	public ExchangeRate getRates(String currency) {
		final ExchangeRate ret = new ExchangeRate(currency);
		
		try {
			final String cql = prepareCQL(currency);
			
			Logger.info(cql);
			
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


	private static void addToBatch(final MutationBatch mb, final ExchangeRate e) {
		final Iterator<Long> dates = e.rates.keySet().iterator();
		while (dates.hasNext()) {
			final Long date = dates.next();
			mb.withRow(COLUMN_FAMILY, date).putColumn(e.currency, e.rates.get(date));	
		}
	}


	private static void addCurrencyColumn(final ExchangeRate exchangeRate) throws ConnectionException {
		KEYSPACE.prepareCqlStatement()
				.withCql("ALTER TABLE exchange_rates ADD " + exchangeRate.currency + " float;")
				.execute();
		
		Logger.debug("Added new currency to ColumnFamily : " + exchangeRate.currency);
	}


	private static boolean isNewCurrency(final ExchangeRate exchangeRate, final Set<String> knownCurrencies) {
		return !knownCurrencies.contains(exchangeRate.currency);
	}


	public Set<String> readKnownCurrencies() throws ConnectionException {
		final Set<String> ret = new HashSet<String>();
		
		final List<ColumnDefinition> defs = KEYSPACE.describeKeyspace().getColumnFamily(COLUMN_FAMILY_NAME).getColumnDefinitionList();
		
		for (ColumnDefinition def : defs) {
			ret.add(def.getName());
		}
		
		return ret;
	}

	
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
