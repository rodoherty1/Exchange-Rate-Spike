package models;


import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import models.dao.ExchangeRateDAO;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import play.Logger;
import play.db.jpa.Model;
import play.libs.F;
import play.libs.WS;
import play.libs.XPath;
import play.libs.WS.HttpResponse;

import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

/*
 * This is the model for the Web App.
 * 
 * This model also captures communication with the Data Access Layer.  This is facilitated by the ExchangeRateDAO.
 * 
 */
public class ExchangeRate extends Model {
    public static final int HISTORY_SIZE = 90;
	private static final int NUMBER_OF_THREADS = 10;

    private static final String URL_EXCHANGE_RATE_HISTORY = "http://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml";

    private static final SimpleDateFormat sdfYearMonthDate = new SimpleDateFormat("yyyy-M-dd");

    /*
     * Interface into the DAL
     */
	public static ExchangeRateDAO dao;
    
	/*
	 * Each ExchangeRate object has a Currency
	 */
    public String currency;
    
    /*
     * Each ExchangeRate object has a collection of Exchange Rates for the past 90 days. 
     */
    public Map<Long, Float> rates = new HashMap<Long, Float>(HISTORY_SIZE);

    /*
     * Thread Pool which is used to asynchronously contact the ECB's website and the Data Access Layer (DAL) 
     */
    private static final ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    
    public ExchangeRate(String currency) {
    	this.currency = currency;
	}

    
    /**
     * Public interface used to get an ExchangeRate for a specified currency
     */
	public static Future<ExchangeRate> get(final String currency) {
		return executor.submit(new Callable() {
			@Override
			public ExchangeRate call() {
				ExchangeRate ret = dao.getRates(currency);
				if (ret.hasNoData()) {
					try {
						refreshAll().get();
						ret = dao.getRates(currency);
					} catch (Exception e) {
						Logger.error("Exchange Rate data cannot be refreshed : " + e.getMessage());
						ret = null; 
					}
				}
				return ret; 
			}
		});
    }
    

    /**
     * Public interface used to refresh ExchangeRate data from the ECB's website for a specified currency
     */
	public static Future<?> refresh(final String currency) {
		return executor.submit(new Runnable() {
			@Override
			public void run() {
		    	final HttpResponse res = getLatestCurrencyRatesFromECB();
		    	final Document xmlDoc = res.getXml();
		    	
		    	final Collection<ExchangeRate> allRates = createExchangeRates(currency, xmlDoc);
		    	dao.insert(allRates.iterator());
			}
		});
    }

    /**
     * Public interface used to refresh all ExchangeRate data from the ECB's website
     */
    public static Future<?> refreshAll() {
		return executor.submit(new Runnable() {
			@Override
			public void run() {
		    	final HttpResponse res = getLatestCurrencyRatesFromECB();
		    	final Document xmlDoc = res.getXml();

		    	final Collection<ExchangeRate> allRates = createExchangeRates(xmlDoc);
		    	dao.insert(allRates.iterator());
			}
		});
    }

    /*
     * Retrieve the latest ExchangeRate XML doc from the ECB's website
     */
	private static HttpResponse getLatestCurrencyRatesFromECB() {
		final F.Promise<WS.HttpResponse> history = WS.url(URL_EXCHANGE_RATE_HISTORY).getAsync();
		
        HttpResponse res = null;
		try {
			res = history.get();
		} catch (Exception e) { 
			Logger.error("Unable to reach the ECB");
		}
        
		return res;
	}

	
    /*
     * Read a list of currencies that are stored in the DB
     */
	public static Future<List<String>> getCurrencies() {
		return executor.submit(new Callable() {
			@Override
			public List<String> call() throws Exception {
				try {
					return dao.readKnownCurrencies();
				} catch (ConnectionException e) {
					throw new IllegalStateException ("Currencies Unavailable : " + e.getMessage(), e);
				}
			}
		});
	}
	
	private static Collection<ExchangeRate> createExchangeRates(Document xmlDoc) {
		return createExchangeRates(null, xmlDoc);
	}

	/*
	 * Parse the ECB's XML doc and retrieve the last 90 days of Exchange Rates  
	 */
	private static Collection<ExchangeRate> createExchangeRates(String userSelection, Document xmlDoc) {
		final Map<String, ExchangeRate> ret = new HashMap<String, ExchangeRate>();

		final Node root = XPath.selectNode("/*", xmlDoc);
		
		final List<Node> history = XPath.selectNodes("Cube/Cube", root);
		
		for (int i=0; i<HISTORY_SIZE; i++) {
			final Node day = history.get(i);
		    final String date = XPath.selectText("@time", day);
		    
		    final List<Node> allCurrencies = XPath.selectNodes("Cube", day);
		    
			for(Node currency: allCurrencies) {
			    final String currencyStr = XPath.selectText("@currency", currency);
			    
			    if ((userSelection == null) || (userSelection.equals(currencyStr))) { // If "Refresh All" or "Refresh only user-selection"   
				    final String rate = XPath.selectText("@rate", currency);
			    	addtoMap(ret, toEpochTime(date), currencyStr, Float.parseFloat(rate));
			    }
		    }
		}
		
		return ret.values();
	}


	/*
	 * Convenience method used during the parsing of the ECB's XML document.
	 */
	private static void addtoMap(Map<String, ExchangeRate> map, Long date, String currency, Float rate) {
		ExchangeRate e = map.get(currency);
		
		if (e == null) {
			e = new ExchangeRate(currency);
			map.put(currency, e);
		}
		
		e.addRate(date, rate);
	}

	
	/**
	 * Add a new rate along with its corresponding date to this ExchangeRate object.  
	 */
    public void addRate(Long date, Float rate) {
    	if ((date != null) && (rate != null)) {
    		this.rates.put(date, rate);
    	}
    }
    
    
	private static long toEpochTime(String dateAsString) {
		try {
			return sdfYearMonthDate.parse(dateAsString).getTime();
		} catch (ParseException e) {
			Logger.error("Error parsing " + dateAsString + " as " + sdfYearMonthDate.toPattern(), e);
			return 0;
		}
	}

	/**
	 * Rates must be sorted by date before they are returned to the Web layer as a response.
	 * This ensures that the data visualisation makes sense!
	 */
	public final Set<Long> getSortedDates() {
		final Set<Long> ret = new TreeSet<Long> (new Comparator<Long>() {
			@Override
			public int compare(Long l1, Long l2) {
				return l1.compareTo(l2);
			}
		});
		
		ret.addAll(rates.keySet());
		
		return ret;
	}

	
    private boolean hasNoData() {
		return this.rates.isEmpty();
	}
}
