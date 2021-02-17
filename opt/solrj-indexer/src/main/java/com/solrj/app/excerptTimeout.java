    /**
     * Configures SolrClient parameters and returns a SolrClient instance.
     * 
     * @return a SolrClient instance
     */
    private static SolrClient getSolrClient() {
        return new HttpSolrClient.Builder(SOLR_CORE_URL).withConnectionTimeout(200000).withSocketTimeout(900000).build();
    }



                                    runWithTimeout(new Runnable() {
                                        @Override
                                        public void run() {
                                          try {
                                              System.out.printf("Parsing file by Tika...\n");
                                              long startTime = System.currentTimeMillis();

                                              // BodyContentHandler(int writeLimit) 100000 default
                                              ContentHandler textHandler = new BodyContentHandler(-1);
                                              Metadata metadata = new Metadata();

                                              autoParser.parse(input, textHandler, metadata, context);
                                              System.out.printf("Extracted\n");

                                              String attach_text = textHandler.toString();
                                              attach_text = attach_text.replace("_", " ");
                                              while (attach_text.indexOf("??")>=0) {
                                                attach_text = attach_text.toString().replace("??", "?");
                                              }
                                            
                                              byte[] utf8Bytes = attach_text.getBytes("UTF-8");
                                              pendSize[0] = pendSize[0] + (int) utf8Bytes.length/1024;
                                              doc.addField("attach_content", attach_text);
                                              doc.addField("ocr", "true");
                                              for(String oneSentece : breakInSenteces(attach_text)) {
                                                doc.addField("emails_sgs", oneSentece);
                                            }
                        

                                              long endTime = System.currentTimeMillis();
                                              long elapsled = (endTime - startTime)/1000;

                                              System.out.printf("Done in %d seconts\n", elapsled);
                                              
                                              //System.exit(0);
                                            } catch (Exception ex ) {
                                                //ex.printStackTrace();
                                                System.out.printf("Interrupted\n");
                                                doc.addField("ocr", "false");
                                            }
                                        }
                                    }, 60, TimeUnit.SECONDS); //400







	    public void runWithTimeout(final Runnable runnable, long timeout, TimeUnit timeUnit) throws Exception {
		    runWithTimeout(new Callable<Object>() {
			    @Override
			    public Object call() throws Exception {
				    runnable.run();
				    return null;
			    }
		    }, timeout, timeUnit);
	    }

	    public <T> T runWithTimeout(Callable<T> callable, long timeout, TimeUnit timeUnit) throws Exception {
		    final ExecutorService executor = Executors.newSingleThreadExecutor();
		    final Future<T> future = executor.submit(callable);
		    executor.shutdown(); // This does not cancel the already-scheduled task.
		    try {
			    return future.get(timeout, timeUnit);
		    }
		    catch (TimeoutException e) {
			    //remove this if you do not want to cancel the job in progress
			    //or set the argument to 'false' if you do not want to interrupt the thread
			    future.cancel(true);
			    //throw e;
                System.out.printf("Interrupted\n");
			    return null;
		    }
		    catch (ExecutionException e) {
			    //unwrap the root cause
			    Throwable t = e.getCause();
			    if (t instanceof Error) {
				    throw (Error) t;
			    } else if (t instanceof Exception) {
				    throw (Exception) e;
			    } else {
				    throw new IllegalStateException(t);
			    }
		    }
	    }
    //}    


