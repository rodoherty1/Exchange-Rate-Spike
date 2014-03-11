package controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import models.ExchangeRate;
import play.mvc.Controller;

/*
 * This is the web layer for the Web App
 */
public class ExchangeRates extends Controller {

	/*
	 * Main entry point into the Web App
	 */
    public static void home() {
    	final List<String> currencies = Arrays.asList(await(ExchangeRate.getCurrencies()));
    	Collections.sort(currencies);
    	
        render(currencies);
    }

    
	/*
	 * User selects a currency
	 */
	public static void get(String currency) {
        final Future<ExchangeRate> f1 = ExchangeRate.get(currency);
    	final ExchangeRate exchangeRate = await(f1);

    	final Set<Long> dates = exchangeRate.getSortedDates();
    	
    	final String json = toJSON(exchangeRate, dates);
    			
    	renderJSON(json);
    }
    

	/*
	 * User requests that the data for a currency is re-read from ECB's website
	 */
	public static void refresh(String currency) {
        await(ExchangeRate.refresh(currency));
        get(currency);
    }
    
	
	/*
	 * User requests that all currency data is re-read from ECB's website
	 */
    public static void refreshAll() {    	
    	await(ExchangeRate.refreshAll());
    	home();
    }

	
    /*
     * Create the JSON that represents the Exchange Rate data.
     * 
     * e.g.
     * { 
     *     dates : [epoch1, epoch2, epoch3],
     *     rates : [1.23, 1.25, 1.27] 
     * }
     */
    private static String toJSON(ExchangeRate exchangeRate, Set<Long> dates) {
    	final StringBuilder sb = new StringBuilder();
    	sb.append("{ \"dates\": [");
    	for (Long date : dates) {
    		sb.append(date); // epoch time
    		sb.append(',');
    	}
    	sb.deleteCharAt(sb.length()-1); /// Remove trailing comma
    	sb.append("],");
    	
    	sb.append("\"rates\": [");
    	for (Long date : dates) {
    		sb.append(exchangeRate.rates.get(date));
    		sb.append(',');
    	}
    	sb.deleteCharAt(sb.length()-1); /// Remove trailing comma
    	sb.append("]}");
    	return sb.toString();
	}
}
