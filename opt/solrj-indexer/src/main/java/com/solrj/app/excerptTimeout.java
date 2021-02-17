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

