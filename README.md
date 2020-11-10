# solr-javascript-client
Google like webinterface for Solr full text search server

# Step 1 – Install Java
sudo apt install openjdk-11-jdk

# Verify active Java version:
java -version

openjdk version "11.0.4" 2019-07-16
OpenJDK Runtime Environment (build 11.0.4+11-post-Ubuntu-1ubuntu218.04.3)
OpenJDK 64-Bit Server VM (build 11.0.4+11-post-Ubuntu-1ubuntu218.04.3, mixed mode, sharing)

# Step 2 – Install Apache Solr on Ubuntu
cd /opt
wget https://archive.apache.org/dist/lucene/solr/8.5.2/solr-8.5.2.tgz
tar xzf solr-8.5.2.tgz solr-8.5.2/bin/install_solr_service.sh --strip-components=2
sudo bash ./install_solr_service.sh solr-8.5.2.tgz

# Step 3 – Create Solr Collection
sudo u solr /opt/solr/bin/solr create -c mycol1 -n data_driven_schema_configs

# Step 4 – Access Solr Admin Panel
http://localhost:8983/

# Step 5 – Enable CORS within the Solr application server
isert into /opt/solr/server/solr-webapp/webapp/WEB-INF/web.xml

<filter>
   <filter-name>cross-origin</filter-name>
   <filter-class>org.eclipse.jetty.servlets.CrossOriginFilter</filter-class>
   <init-param>
     <param-name>allowedOrigins</param-name>
     <param-value>*</param-value>
   </init-param>
   <init-param>
     <param-name>allowedMethods</param-name>
     <param-value>GET,POST,OPTIONS,DELETE,PUT,HEAD</param-value>
   </init-param>
   <init-param>
     <param-name>allowedHeaders</param-name>
     <param-value>origin, content-type, accept</param-value>
   </init-param>
 </filter>

 <filter-mapping>
   <filter-name>cross-origin</filter-name>
   <url-pattern>/*</url-pattern>
 </filter-mapping>
 
 # Step 6 – Configure your collection
 For now we just take ready configureation from techproducts example:
 sudo -u solr /opt/solr/bin/solr -e techproducts
 then go to /opt/solr/server/data/techprducts/conf and copy all the files into /var/solr/data/mycol1
 
 Set default index directory in /opt/solr/bit/sorl.in.sh:
 SOLR_HOME=/var/solr/data
 
 Build suggester (https://lucene.apache.org/solr/guide/8_7/suggester.html#dictionary-implementations). In core dir (/var/solr/data/mycol1):
 solrconfig.xml
 <searchComponent name="suggest" class="solr.SuggestComponent">
  <lst name="suggester">
    <str name="name">mySuggester</str>
    <str name="lookupImpl">FuzzyLookupFactory</str>
    <str name="dictionaryImpl">DocumentDictionaryFactory</str>
    <str name="field">cat</str>
    <str name="weightField">price</str>
    <str name="suggestAnalyzerFieldType">string</str>
    <str name="buildOnStartup">false</str>
  </lst>
</searchComponent>
<requestHandler name="/suggest" class="solr.SearchHandler" startup="lazy">
  <lst name="defaults">
    <str name="suggest">true</str>
    <str name="suggest.count">10</str>
  </lst>
  <arr name="components">
    <str>suggest</str>
  </arr>
</requestHandler>

managed_schema:
<fieldType class="solr.TextField" name="textSuggest" positionIncrementGap="100">
  <analyzer>
    <tokenizer class="solr.StandardTokenizerFactory"/>
    <filter class="solr.LowerCaseFilterFactory"/>
  </analyzer>
</fieldType>

working by: http://localhost:8983/solr/test/suggest?suggest=true&suggest.build=true&suggest.dictionary=mySuggester&suggest.q=apple
  
 # Step 6 – Index your files
 sudo -u solr /opt/solr/bin/post -c mycol1 rootDir/ -m1024M
 
 # Step 6 – Setup the webinterface
 Copy /www/html folder into your webserver and configure conf/conf.js
 
 
