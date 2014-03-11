Exchange-Rate-Spike
===================

This is a simple spike of a web app that will display the last 90 days of currency exchange rates in the browser.

* The browser will display the data using the D3 library.
* The data will be retrieved from http://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml
* The data will be stored in Cassandra
* The web app will be written using The Play Framework.


Todo
====

* Create basic web app
* Display in the browser either a static page, or a template page.
* Install Cassandra 1.1.12
* Install Astyanax 1.56.26

* Design column-store table
* Install table
* Insert some dummy data to query against
* Define Domain Model
* Connect the browser to the datasource
* Get a proof-of-concept of Astyanax
* Get a simple round-trip from the browser to the db
* Spec out the REST interface
* Find the best URL to use to query exchange rate data of all currencies

* Write exchange results into DB table.
* I need to stream the results back to the browser
* Add a menu to allow the user to select a different currency
* Add a refresh all currencies button
* Write the D3 code which will displays the chart for the last 90 days
* Align the D3 table with the table schema
* Is there an elegant way to bind Cassandra rows with the source code (e.g. like the SpringData project for MongoDB) 


