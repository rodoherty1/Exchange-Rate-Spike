CREATE KEYSPACE Boxever
WITH strategy_class = SimpleStrategy 
AND strategy_options:replication_factor='1';

USE Boxever;

CREATE TABLE exchange_rates (
  date bigint PRIMARY KEY
);

ALTER TABLE exchange_rates ADD USD float;
ALTER TABLE exchange_rates ADD GBP float;
ALTER TABLE exchange_rates ADD RUB float;
INSERT INTO exchange_rates (date, USD) VALUES (1394305208, 1.27);
INSERT INTO exchange_rates (date, GBP) VALUES (1394305208, 0.78);
INSERT INTO exchange_rates (date, RUB) VALUES (1394305208, 123);

