package com.solrj.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MapSolrParams;

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

    private static final SolrClient getSolrClient() {
	return new HttpSolrClient.Builder(SOLR_CORE_URL).withConnectionTimeout(50000).withSocketTimeout(30000).build();
    }

    public static void main(String[] args) {
        System.out.println("======== SolrJ Example ========");
        App example = new App();

        // Clear index
        /*                               
        try {
            solrClient.deleteByQuery("*:*");
        } catch (SolrServerException | IOException e) {
            System.err.printf("\nFailed to index articles: %s", e.getMessage());
        }
	 */


        try {
            solrClient.commit();
        } catch (SolrServerException | IOException e) {
            System.err.printf("\nFailed to index articles: %s", e.getMessage());
        }

        //System.exit(0);

        example.indexFiles();

    }



    public void indexFiles() {
        Path dir = Paths.get("/mnt/data/public");
        try {
            Files.walk(dir).forEach(path -> reallyIndexFiles(path.toFile()));
        } catch (Exception ex ) {
            System.err.printf("\nFailed to scan files: %s", ex.getMessage());
            ex.printStackTrace();            
        }
    }


    public void reallyIndexFiles(File file) {

        if (file.isFile()) {
            
            String path_prefix = "/mnt/data/public/";

            String fullPath = file.getAbsolutePath();
            String relPath = file.getParent().replace(path_prefix, "");
            String fileName = file.getName();
            String relFullPath = relPath + "/" + fileName;
            String title = fileName.substring(0, fileName.lastIndexOf("."));
            String search_area = "public";
            String uid = search_area + relFullPath.hashCode();

            try {    

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

                SolrClient solrClientNew = getSolrClient();

                // Check if document exists
                SolrQuery sq = new SolrQuery();
                sq.setQuery("*:*");
                sq.addFilterQuery("id:" + uid);
                QueryResponse res_uid = solrClientNew.query(sq);
                SolrDocumentList docs_uid = res_uid.getResults();
                int numfound_uid = (int) docs_uid.getNumFound();
                if (numfound_uid != 0) {
                    System.out.printf("*****   Already exists. Skpping...   *****\n");
                    return;
                }


                final SolrInputDocument doc = new SolrInputDocument();
                doc.addField("id", uid);
                doc.addField("search_area", search_area);
                doc.addField("subject", title);
                doc.addField("attach_dir", relPath);
                doc.addField("attach", fileName);

                final FileInputStream input = new FileInputStream(fullPath);

                ContentHandler textHandler = new BodyContentHandler(-1);
                Metadata metadata = new Metadata();
                
                autoParser.parse(input, textHandler, metadata, context);

                String attach_text = textHandler.toString();

                doc.addField("attach_content", attach_text);


                for(String oneSentece : breakInSenteces(attach_text)) {
                    doc.addField("files_sgs", oneSentece);
                }


                try {
                    solrClientNew.add(doc);
                    System.out.printf("File: %s\n", relFullPath);
                } catch (Exception ex ) {
                    ex.printStackTrace();            
                }

            } catch (Exception ex ) {
                ex.printStackTrace();            
            }
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
}
