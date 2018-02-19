package de.jn.paraphrases.crawler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.jn.paraphrases.db.entity.Fragment;
import de.jn.paraphrases.db.repository.FragmentRepository;

/**
 * Created by jan on 29.08.17.
 */
@Component
@Profile("vroniplag")
public class VroniplagCrawler {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    FragmentRepository pageRepository;

    Pattern fragmentMatcher = Pattern.compile(".*/wiki/.*/Fragment.*");

    String[] seeds = {
//            "http://de.vroniplag.wikia.com/wiki/Kategorie:BauernOpfer",
//            "http://de.vroniplag.wikia.com/wiki/Kategorie:KomplettPlagiat",
//            "http://de.vroniplag.wikia.com/wiki/Kategorie:ShakeAndPaste",
            "http://de.vroniplag.wikia.com/wiki/Kategorie:Verschleierung",
//            "http://de.vroniplag.wikia.com/wiki/Kategorie:Versch%C3%A4rftesBauernopfer",
//            "http://de.vroniplag.wikia.com/wiki/Kategorie:%C3%9CbersetzungsPlagiat"
    };

    @PostConstruct
    public void crawl(){
        Arrays.stream(seeds)
            .flatMap(url -> crawlSeed(url).stream())
            .forEach(url -> crawlPage(url));
    }

    public List<String> crawlSeed(String url){
        logger.info("download seed " + url);

        List<String> crawledUrls = new ArrayList<>();
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            logger.error("exception fetching " + url, e);
            return crawledUrls;
        }
        Elements links = doc.select("a[href]");
        for (Element element : links) {
            String page = element.attr("abs:href");
            if (fragmentMatcher.matcher(page).matches() && !page.contains("Analyse")) {
                crawledUrls.add(page);
            }

        }

        Element paginator = doc.select("a.paginator-next").first();
        if(paginator != null && !paginator.hasClass("disabled")){
            String nextUrl = paginator.attr("abs:href");
            crawledUrls.addAll(crawlSeed(nextUrl));
        }
        return crawledUrls;
    }

    public void crawlPage(String url){

        if(pageRepository.findOneByUrl(url) != null){
            logger.info("skip page " + url);
            return;
        }

        logger.info("download page " + url);
        
        try {
            Document doc = Jsoup.connect(url).get();

            Fragment plagiat = new Fragment();

            plagiat.setUrl(url);

            String fragmentIdentifier = doc.select("h1").first().text();
            plagiat.setFragmentIdentifier(fragmentIdentifier);

            String typus = doc.select("div.fragment dl").first().select("dd").text();
            plagiat.setType(typus);

            String gesichtetStr = doc.select("div.fragment dl a img").attr("alt");
            boolean gesichtet = gesichtetStr.equals("Yes");
            plagiat.setGesichtet(gesichtet);

            String fullHtml = doc.select("#WikiaMainContent").first().toString();
            plagiat.setFullHtml(fullHtml);

            Element fragmentTr = doc.select("div.fragment table").get(1).select("tr").last();

            Element sourceElement= fragmentTr.select("td").last();
            String sourceText = trimAfterHr(sourceElement.toString());
            plagiat.setSourceText(sourceText);

            String sourceTextRaw = fragmentTr.select("td").last().toString();
            plagiat.setRawSourceText(sourceTextRaw);

            Element plagiatElement = fragmentTr.select("td").first();
            String plagiatText = trimAfterHr(plagiatElement.toString());
            plagiat.setPlagiatText(plagiatText);

            String plagiatTextRaw = fragmentTr.select("td").first().toString();
            plagiat.setRawPlagiatText(plagiatTextRaw);

            plagiat.setSource(Fragment.Source.Vroniplag.name());
            pageRepository.save(plagiat);
        } catch (IOException | NullPointerException e) {
            logger.error("exception fetching " + url, e);
        }

    }

    public String trimAfterHr(String html){
        String cleaned = null;
        int index = html.toLowerCase().indexOf("<hr>");
        if(index<0){
            cleaned = html;
        } else{
            cleaned = html.substring(0,index) + "</td>";
        }
        Document doc = Jsoup.parse(cleaned);
        return doc.text();
    }
}
