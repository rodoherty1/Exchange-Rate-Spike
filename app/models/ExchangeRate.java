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

public class ExchangeRate extends Model {
    public static final int HISTORY_SIZE = 90;

    private static final String URL_EXCHANGE_RATE_HISTORY = "http://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml";

    private static final SimpleDateFormat sdfYearMonthDate = new SimpleDateFormat("yyyy-M-dd");
    private static final SimpleDateFormat sdfMonthDate = new SimpleDateFormat("MMM-dd");

	private static final int NUMBER_OF_THREADS = 10;
    
	public static ExchangeRateDAO dao;
    
    public String currency;
    
    public Map<Long, Float> rates = new HashMap<Long, Float>(HISTORY_SIZE);

    private static final ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    
    public ExchangeRate(String currency) {
    	this.currency = currency;
	}

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

	
	public static Future<String[]> getCurrencies() {
		return executor.submit(new Callable() {
			@Override
			public String[] call() throws Exception {
				try {
					final Set<String> currencies = dao.readKnownCurrencies();
					return currencies.toArray(new String[currencies.size()]);
				} catch (ConnectionException e) {
					return new String[] {"Currencies Unavailable"};
				}
			}
		});
	}
	
	private static Collection<ExchangeRate> createExchangeRates(Document xmlDoc) {
		return createExchangeRates(null, xmlDoc);
	}

	
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
	 * 
	 */
	private static void addtoMap(Map<String, ExchangeRate> map, Long date, String currency, Float rate) {
		ExchangeRate e = map.get(currency);
		
		if (e == null) {
			e = new ExchangeRate(currency);
			map.put(currency, e);
		}
		
		e.addRate(date, rate);
	}

	
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

    
	public final String asString(long epoch) {
		return sdfMonthDate.format(new Date(epoch));
	}

	

}
