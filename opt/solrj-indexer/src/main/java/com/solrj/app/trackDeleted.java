    public void excludeDeleted() {
        System.out.println("Re-Indexing by Atomic Updates...");
        SolrClient solrClient = getSolrClient();

        // constructs a MapSolrParams instance
        Map<String, String> queryParamMap = new HashMap<String, String>();

        queryParamMap.put("q", "id:*");
        queryParamMap.put("rows", "0");

        MapSolrParams queryParams = null;
        queryParams = new MapSolrParams(queryParamMap);
        QueryResponse response = null;

        try {
            response = solrClient.query(queryParams);
        } catch (SolrServerException | IOException e) {
            System.err.printf("\nFailed to search articles: %s", e.getMessage());
        }

        if (response != null) {
            SolrDocumentList documents = response.getResults();
            //System.out.printf("Found %d documents\n", documents.getNumFound());
            long numFound = documents.getNumFound();
            int rows = 1000;
            long max_id_page = (long) Math.round(Math.ceil((double) numFound / rows));
            for (int n = 0; n < max_id_page; n++) {
                long start = n * rows;
                //int id_end = max_id - n * id_page;
                queryParamMap = new HashMap<String, String>();
                queryParamMap.put("q", "id:*");
                queryParamMap.put("start", String.valueOf(start));
                queryParamMap.put("rows", String.valueOf(rows));
                queryParamMap.put("fl", "id");
                
                queryParams = new MapSolrParams(queryParamMap);
                System.out.printf("The range is: %d + %d rows out of %d\n", start, rows, numFound); 

                try {
                    response = solrClient.query(queryParams);
                } catch (SolrServerException | IOException e) {
                    System.err.printf("\nFailed to search articles: %s", e.getMessage());
                }

                if (response != null) {
                    
                    documents = response.getResults();
                    SolrInputDocument doc = new SolrInputDocument();

                    for (SolrDocument document : documents) {

                        string fileName = document.getFieldValue("id");
                        
                        
                        //System.out.printf("Id = %s\n", doc);
                        //System.exit(0);

                        // send the documents to Solr
                        try {
                            //System.out.printf("Adding the doc...\n");
                            solrClient.add(doc);
                            Thread.sleep(0);
                            //System.out.printf("Successfully added...\n");
                        } catch (SolrServerException | IOException | InterruptedException e ) {
                            System.err.printf("\nFailed to index articles: %s", e.getMessage());
                        }
                    }
                    
                    try {
                        System.out.printf("Committing...\n");
                        solrClient.commit();
                        System.out.printf("Successfully commited...\n");
                        //System.exit(0);
                    } catch (SolrServerException | IOException e) {
                        System.err.printf("\nFailed to index articles: %s", e.getMessage());
                    }
                    
                }

            }
       }

    }
