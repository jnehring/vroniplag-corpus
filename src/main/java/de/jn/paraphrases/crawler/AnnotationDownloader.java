package de.jn.paraphrases.crawler;

import java.util.Iterator;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import de.jn.paraphrases.db.entity.Fragment;
import de.jn.paraphrases.db.repository.FragmentRepository;

@Component
@Profile("annotation-downloader")
public class AnnotationDownloader {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	FragmentRepository fragmentRepository;
	
	@Transactional
	public void save(Fragment p){
//		System.out.println("source: " + p.getRawSourceText());
//		System.out.println("plagiat: " + p.getRawPlagiatText());
		fragmentRepository.save(p);
	}

	@PostConstruct
	public void onload(){
		
		Iterator<Fragment> itr = fragmentRepository.findAll().iterator();
		while(itr.hasNext()){
				Fragment plagiat = null;
			try{
				plagiat = itr.next();
//				if(!(plagiat.getUrl().equals("http://de.vroniplag.wikia.com/wiki/Aaf/Fragment_009_01")))
//					continue;
				WebClient webClient = new WebClient();
				
				HtmlPage page = webClient.getPage(plagiat.getUrl());
		        DomElement domElem = (DomElement) page.getElementById("WikiaMainContent");
		        List<DomElement> allSrcTr = domElem.getByXPath("//div[@class='fragment']/table[position()=2]//tr[last()]/td[last()]");
		        List<DomElement> allPlagiatTr = domElem.getByXPath("//div[@class='fragment']/table[position()=2]//tr[last()]/td[1]");
		        DomElement srcTr = allSrcTr.get(0);
		        DomElement plagiatTr = allPlagiatTr.get(0);
		        String srcRaw = srcTr.asXml();
		        String plagRaw = plagiatTr.asXml();
		        
		        plagiat.setRawPlagiatText(VroniplagCrawler.removeInvalidUTF8(plagRaw));
		        plagiat.setRawSourceText(VroniplagCrawler.removeInvalidUTF8(srcRaw));	
				save(plagiat);
				
				webClient.close();
			} catch(Exception e){
				logger.error("exception fetching " + plagiat.getUrl(), e);
				plagiat.setRawPlagiatText("");
				plagiat.setRawSourceText("");
				save(plagiat);
			} 
		}
	}

}
