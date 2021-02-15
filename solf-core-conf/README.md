# Solr's core configuration files
Each Solr core located in its own directory. Such a directory has /data subfolder for the actual index and /conf subfolder for configuration settings of the core.

# solrconfig.xml
Main configuration file with all the date processors definitions.

# managed-schema
Main index file defining all index fields

# Update Solr core on the fly
curl http://localhost:8983/solr/mycore/update?commit=ture

# Build suggester dictionary
curl http://localhost:8983/solr/mycore/suggest?build=true
