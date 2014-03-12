CREATE KEYSPACE Boxever
WITH strategy_class = SimpleStrategy 
AND strategy_options:replication_factor='1';

USE Boxever;

CREATE TABLE exchange_rates (
  date bigint PRIMARY KEY
);
