Exchange-Rate-Spike
===================

This is a simple spike of a web app that will display the last 90 days of currency exchange rates in the browser.

Functional Overview
===================

Write a non-blocking Java web application which will consume a rolling window of the last 90 days of exchange rate data from a third-party API. 

This data from the third-party is required to be stored in the database (Cassandra & Astyanax Client) to avoid unnecessary network calls to the third-party API.

There will be a UI component which will allow the end-user to select an available exchange rate to visualise its performance.

- the retrieval of the rates should be implemented in a non-blocking fashion against the database.
- if the database does not contain the data then a lazy load is performed against the third-party API in a non-blocking fashion to populate the database.
- database & third-party API stampedes should be avoided when lazy loading the data from the third-party API.
- the exchange rates will be available via a REST call.
- a selected exchange rate will be rendered to an end-user via a time-based graph visualisation.

User Story
==========
The web application is running

- The end-user browsers the application URL.
- The end-user is presented with a visualisation containing the EUR-USD data points plotted for the last 90 days.
- The end-user can select other rates of exchange from a menu displaying the available conversions.
- The end-user can force a real-time non-blocking refresh of the currently selected exchange rate.
- The end-user can force a real-time refresh of all exchange rates concurrently.

Resources
==========
Please use the following platform/libraries
- Playframework version 1.2.5 - Java Web Application - http://www.playframework.org/
- AJAX/JQuery - http://jquery.com/
- D3 - Javascript Visualisation Engine - http://d3js.org/
- Exchange Rate API - http://www.ecb.int/stats/exchange/eurofxref/html/index.en.html
- Cassandra 1.1.12 - http://cassandra.apache.org
- Astyanax 1.56.26 - https://github.com/Netflix/astyanax

Ensure that 
===========
- The code is formatted
- The code has appropriate Javadoc and comments.
- Include the Cassandra.ddl for population of the Cassandra Keyspace and ColumnFamilies




