package models.dao;

import java.util.Iterator;
import java.util.List;

import models.ExchangeRate;

import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

public interface ExchangeRateDAO {

	ExchangeRate getRates(String currency);

	void insert(Iterator<ExchangeRate> iterator);

	List<String> readKnownCurrencies() throws ConnectionException;

}
