package models.dao;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Future;

import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

import models.ExchangeRate;

public interface ExchangeRateDAO {

	ExchangeRate getRates(String currency);

	void insert(Iterator<ExchangeRate> iterator);

	Set<String> readKnownCurrencies() throws ConnectionException;

}
