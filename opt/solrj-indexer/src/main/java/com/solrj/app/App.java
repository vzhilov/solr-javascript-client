package com.solrj.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

//import java.net.URLEncoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import java.util.Locale;
import java.text.SimpleDateFormat;
import java.text.BreakIterator;

//import java.sql.*;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;
import org.apache.solr.common.util.NamedList;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;


import org.xml.sax.ContentHandler;

/**
 * 
 * @author Kevin Yang
 *
 */
public class App {
    /**
     * The Solr instance URL running on localhost.
     */
    private static final String SOLR_CORE_URL = "http://localhost:8983/solr/poogle";

    /**
     * The static solrClient instance.
     */
    private static final SolrClient solrClient = getSolrClient();

    private static int heapDocs, totalDoc, inIndex = 0;

    private static String indexRoot = "/mnt/data/public/";

    private static final SolrClient getSolrClient() {
	return new HttpSolrClient.Builder(SOLR_CORE_URL).withConnectionTimeout(50000).withSocketTimeout(30000).build();
    }

    public static void main(String[] args) {
		long cTime = System.currentTimeMillis();
		String sTime = FileTime.fromMillis(cTime).toString();
        System.out.printf("======== SolrJ Indexer has started: %s ========\n", sTime);
        App example = new App();

        // Clear index
        /*            		
       				
        try {
            solrClient.deleteByQuery("*:*");
        } catch (SolrServerException | IOException e) {
            System.err.printf("\nFailed to index articles: %s", e.getMessage());
        }
		//System.exit(0);
		*/
		
        try {
            solrClient.commit();
        } catch (SolrServerException | IOException e) {
            System.err.printf("Failed to update the index: %s\n", e.getMessage());
			System.err.printf("Trying to restart SOLR");

/*  TODO

			InputStreamReader input;
			OutputStreamWriter output;
			

			String[] cmd = {"/bin/bash", "-c", "/usr/bin/sudo -s /opt/solr/bin/solr restart"};
			try {
				//Runtime.getRuntime().exec(cmd);
				Process process = new ProcessBuilder(cmd).start();
				ouput = new OutputStreamWriter(process.getOutputSteam());
				input = new InputStreamReader(process.getInputStream());
				
				int bytes, tryies = 0;
				char buffer[] = new char[1024];
				
				while (bytes = input.read(buffer, 0, 1024)) != -1) {
					if(bytes == 0)
						continue;
					String data = String.valueOf(buffer, 0, bytes);
					System.out.println(data);
					
					if (data.contains("[sudo] password")) {
						
					}
				}
				
				
				
			} catch (IOException ex) {
				System.err.printf("\nFailed to start: %s", ex.getMessage());
			}

				
*/
        }



/*
// Usefull for first run through

		try {					
			SolrQuery ts = new SolrQuery();
			ts.setQuery("*:*");
			ts.setRows(0);
			QueryResponse tsRes = solrClient.query(ts);
			SolrDocumentList tsDocs = tsRes.getResults();
			inIndex = (int) tsDocs.getNumFound();
			System.out.printf("Number of documents in the index: %d\n", inIndex);
		} catch (SolrServerException | IOException ex ) {
			ex.printStackTrace();
			return;
		}		
*/

		try {					
            File trackFile = new File("track.log");
            if (trackFile.isFile()) {
				String trackFileContent = new String(Files.readAllBytes(Paths.get("track.log")));
				System.out.printf("Indexed so far %s", trackFileContent);
				inIndex = Integer.parseInt(trackFileContent.trim());
			} else {
				inIndex = 0;
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}		

		// Cron runs this script every 5 minutes. If no documents were added for the last 5 minutes it means Tika parser failed on a faulty file and the script exited. So we start all over ignoring the faulty file.
		try {					
			SolrQuery ts = new SolrQuery();
			ts.setQuery("*:*");
			ts.addFilterQuery("last_added: [NOW-5MINUTE TO NOW]");
			ts.setRows(0);
			QueryResponse tsRes = solrClient.query(ts);
			SolrDocumentList tsDocs = tsRes.getResults();
			int tsNumFound = (int) tsDocs.getNumFound();
			if (tsNumFound == 0) {
				example.indexFiles();
				example.excludeDeleted();
			} else {
				System.out.printf("*****   Another Indexer is running. Quiting...   *****\n");
				return;					
			}
		} catch (SolrServerException | IOException ex ) {
			ex.printStackTrace();
			return;
		}		
    }



    public void indexFiles() {
        Path dir = Paths.get(indexRoot);
		
		try {
		Stream<Path> files = Files.walk(dir);

			// Index all files
            files.forEach(path -> reallyIndexFiles(path.toFile()));

			// Delete tracking file used for resuming
            File trackFile = new File("track.log");
            if (trackFile.isFile()) {
				trackFile.delete();
			}


		} catch (IOException e) {
           System.err.printf("\nFailed to scan files: %s", e.getMessage());
 
		}
		
    }


    public void reallyIndexFiles(File file) {

        String path_prefix = indexRoot;

		String fullPath = file.getAbsolutePath();
		String relPath = file.getParent().replace(path_prefix, "");
		String fileName = file.getName();
		String relFullPath = relPath + "/" + fileName;

		// Store the file time for further update check
		long curTime = System.currentTimeMillis();
		FileTime ft2 = FileTime.fromMillis(curTime);

		
        if (file.isFile() && fileName.lastIndexOf(".") >= 0) {
			totalDoc++;			
	
			if (totalDoc < (inIndex - 100)) return;

			System.out.printf("%d. %s", totalDoc, relFullPath);
	
            String title = fileName.substring(0, fileName.lastIndexOf("."));
            String search_area = "public";
			
            //String uid = search_area + relFullPath.hashCode();
            String uid = relFullPath;

			final AutoDetectParser autoParser = new AutoDetectParser();

			TesseractOCRConfig tessConf = new TesseractOCRConfig();
			tessConf.setLanguage("rus+eng");
			tessConf.setEnableImageProcessing(1);

			PDFParserConfig pdfConf = new PDFParserConfig();
			pdfConf.setOcrDPI(300);
			pdfConf.setDetectAngles(true);
			pdfConf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);

			final ParseContext context = new ParseContext();            
			context.set(TesseractOCRConfig.class, tessConf);
			context.set(PDFParserConfig.class, pdfConf);

			//SolrClient solrClientNew = getSolrClient();



			int numfound_uid = 0;
			// Check if document exists
			try {
				SolrQuery sq = new SolrQuery();
				sq.setQuery("*:*");
				sq.addFilterQuery("id:\"" + uid + "\"");
				sq.setRows(0);
				//System.out.printf("\n*****   UID:  %s\n", uid);
				//System.out.printf("\n*****   Solr:  %s\n", sq.toString());
				QueryResponse res_uid = solrClient.query(sq);
				SolrDocumentList docs_uid = res_uid.getResults();
				
				numfound_uid = (int) docs_uid.getNumFound();
			} catch (SolrServerException | IOException ex ) {
				System.out.printf("\n*****   Solr Query failed   *****\n");
				ex.printStackTrace();            
			}

			if (numfound_uid != 0) {
				System.out.printf(" [Exists, skipping %s]\n", ft2.toString());

				if (heapDocs > 1000) {
					final SolrInputDocument heapDoc = new SolrInputDocument();
					heapDoc.addField("id", "lock");
					heapDoc.addField("search_area", "lock");
					//long curTime = System.currentTimeMillis();
					//FileTime ft = FileTime.fromMillis(curTime);
					heapDoc.addField("last_added", ft2.toString());
					System.out.printf("\nUpdate index lock at %s\n", ft2.toString());


					try {
						solrClient.add(heapDoc);
						solrClient.commit();
					} catch (SolrServerException | IOException e) {
						System.err.printf("\nFailed to index articles: %s\n", e.getMessage());
					}

					try {
						PrintWriter trackFile = new PrintWriter("track.log");
						trackFile.println(totalDoc);
						trackFile.close();
					} catch (FileNotFoundException e) {
						e.printStackTrace();            
					}						
					heapDocs = 0;
					
					//System.exit(0);
				} else {
					heapDocs++;
				}
				return;
			}


			// Add basic data to the document
			final SolrInputDocument doc = new SolrInputDocument();
			doc.addField("id", uid);
			doc.addField("search_area", search_area);
			doc.addField("subject", title);
			doc.addField("attach_dir", relPath);
			doc.addField("attach", fileName);
			doc.addField("last_added", ft2.toString());

			try {
				FileTime ft1 = Files.getLastModifiedTime(file.toPath());
				doc.addField("last_modified", ft1.toString());
			} catch (Exception ex ) {
				System.out.printf("\n*****   Failed to check file date/time   *****\n");
				ex.printStackTrace();            
			}


			// We add empty file content in case the content extraction will fail
			String attach_text = "";
			doc.addField("attach_content", attach_text);

			// Adding new document with empy content to the index 
			try {
				solrClient.add(doc);
			} catch (Exception ex ) {
				ex.printStackTrace();            
			}

			// Let try to extact the actual file content
			
			
			try {
				final FileInputStream input = new FileInputStream(fullPath);
				ContentHandler textHandler = new BodyContentHandler(-1);
				Metadata metadata = new Metadata();
				//System.out.printf("*****   Start extraction   *****\n");
				autoParser.parse(input, textHandler, metadata, context);
				attach_text = textHandler.toString();

//				System.out.printf("Got content: %s\n", attach_text);

			} catch (Exception ex ) {
				System.out.printf("\n*****   Tika Parser failed ...   *****\n");
				ex.printStackTrace();            
			}

				//System.out.printf("*****   Adding content to doc ...   *****\n");

			doc.setField("attach_content", attach_text);
				//System.out.printf("*****   Breaking on sentences ...   *****\n");

			// Break extracted content on sentences for search suggester building
			for(String oneSentece : breakInSenteces(attach_text)) {
				doc.addField("files_sgs", oneSentece);
			}
				//System.out.printf("*****   Adding the full doc to index ...   *****\n");

			// Adding final document with extracted content to the index
			try {
				solrClient.add(doc);
				System.out.printf(" [Added %s]\n", ft2.toString());
			} catch (Exception ex ) {
				ex.printStackTrace();            
			}
        } else {
			//System.out.printf(" [Not a file]\n", ft2.toString());
		}
    }

    private static List<String> breakInSenteces (String text) {

        BreakIterator iterator = BreakIterator.getSentenceInstance(new Locale("ru", "RU"));
        iterator.setText(text);
        int start = iterator.first();

        List<String> sent = new ArrayList<String>();

        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String email_pattern = "([^.@\\s]+)(\\.[^.@\\s]+)*@([^.@\\s]+\\.)+([^.@\\s]+)";
            String sentence = text.substring(start,end).replaceAll(email_pattern, "")
                //.replace("()", "")
                .replace(" - ", " ")
                .replace(" – ", " ")
                .replace("“", "")
                .replace("«", "")
                .replace("»", "")
                .replaceAll("[\\p{Punct}&&[^-]]+", " ")
                .replaceAll("\\p{Blank}{2,}+", " ")
                .replace("\n", " ")
                .replace("\t", " ");
            String sentences[] = sentence.split("  |\\.|\\r");
            for (int i = 0; i < sentences.length; i++) {
                String snt = sentences[i].replace("  ", " ").trim();

                Pattern nonSign = Pattern.compile("\\S+\\sнаписал\\sа\\sв\\s\\d{4}");
                Matcher matcher = nonSign.matcher(snt);

                if (snt.length() > 7 && !matcher.find()) {
                    /*
                    snt = snt.replaceAll("/\\./g+$", "");
                    snt = snt.replaceAll(",+$", "");
                    snt = snt.replaceAll(":+$", "");
                    snt = snt.replaceAll("!+$", "");
                    snt = snt.replaceAll("\\?+$", "");
                    snt = snt.replaceAll(";+$", "");
                    //snt = snt.replaceAll("и т\\.п\\.$", "");
                    */
                    
                    //System.out.printf("Sentence: %s\n", snt);
                    if (snt.length() > 84) {
                        String words[] = snt.split(" ");
                        if (words.length > 5) {
                            String subSent = "";
                            int wordsCount = 0;
                            for (String word : words) {
                                if (subSent.length() <= 74) {
                                    subSent += word + " ";
                                    wordsCount++;
                                } else {
                                    if (wordsCount > 4) {
                                        sent.add(subSent.trim());
                                    }
                                    subSent = "";
                                }
                            }
                        }
                        /*
                        String bsent[] = snt.split("(?<=\\G.{72})");
                        for (int ii = 0; ii < bsent.length; ii++) {
                            String bsnt = bsent[ii].trim();
                            if (bsnt.lastIndexOf(" ") > 60 && ii < (bsent.length - 1)) {
                                bsnt = bsnt.substring(0, bsnt.lastIndexOf(" ")+1);
                            }
                            if (bsnt.indexOf(" ") > 0 && bsnt.indexOf(" ") < 10 && ii > 0) {
                                bsnt = bsnt.substring(bsnt.indexOf(" ")+1, bsnt.length() - 1);
                            }
                            sent.add(bsnt.trim());
                        }
                        */
                    } else {
                        sent.add(snt);
                    }


                    
                }
            }
        }
        return sent;
    }
	
	
    public void excludeDeleted() {
        System.out.println("Checking index for non-existant documents...");
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
            System.err.printf("\nFailed to get data from index: %s", e.getMessage());
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
                        
                        String pathPrefix = "/mnt/data/public/";
                        String path = document.getFieldValue("id").toString();

                        File fileName = new File(pathPrefix + path);
                        
                        if (!fileName.isFile()) {
                            // remove deleted document
                            try {
                                System.err.printf("\nDeleting non-existant document: %s", document.getFieldValue("id"));
								solrClient.deleteByQuery("id:\"" + document.getFieldValue("id") + "\"");
                            } catch (SolrServerException | IOException e ) {
                                System.err.printf("\nFailed to delete a document: %s", e.getMessage());
                            }
                        }
                    }
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
