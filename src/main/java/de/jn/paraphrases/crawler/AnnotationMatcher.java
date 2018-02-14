package de.jn.paraphrases.crawler;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.jn.paraphrases.db.entity.Annotation;
import de.jn.paraphrases.db.entity.Fragment;
import de.jn.paraphrases.db.repository.AnnotationRepository;
import de.jn.paraphrases.db.repository.FragmentRepository;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;

@Component
@Profile("annotation-matcher")
public class AnnotationMatcher {

	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	AnnotationRepository annotationRepository;

	@Autowired
	FragmentRepository fragmentRepository;
	
	@Autowired
	ParaphrasePostProcessor postProcessor;

	@Value("${openNLP.sent.path}")
	String sentenceDetectorPath;

	SentenceDetectorME sentenceDetector;
	Set<String> addedPairs = new HashSet<String>();
	
	 @Transactional
	 public void save(Annotation a){
		 annotationRepository.save(a);
	 }
	 
	public void printExtractedAnnotations(List<String> text, List<Map<String, Map.Entry<Integer, Integer>>> offsetMaps){
		assert(text.size()==offsetMaps.size());
		for (int i=0; i<offsetMaps.size(); i++){
			String paragraph = text.get(i);
			Map<String, Map.Entry<Integer, Integer>> paragraphOffsetMap = offsetMaps.get(i);
			Collection<Map.Entry<Integer, Integer>> paragraphOffsets = paragraphOffsetMap.values();
			paragraphOffsets.forEach(offset -> System.out.println(paragraph.substring(offset.getKey(),
					offset.getValue())));
		}
	}
	 
	@PostConstruct
	public void match() throws FileNotFoundException, IOException {
		sentenceDetector = new SentenceDetectorME(new SentenceModel(new FileInputStream(sentenceDetectorPath)));
		
		Iterator<Fragment> itr = fragmentRepository.findAll().iterator();
		while (itr.hasNext()) {
			Fragment plagiat = itr.next();

			if(!(plagiat.getUrl().equals("http://de.vroniplag.wikia.com/wiki/Aaf/Fragment_009_01")))
				continue;
			List<String> plagiatElements = null;
			List<String> srcElements = null;
			List<String> allSrcSents = new ArrayList<String>();
			
			try {
				List<List<String>> plagiatSpans = new ArrayList<List<String>>();
				List<List<String>> srcSpans = new ArrayList<List<String>>();
				System.out.println(plagiat.getUrl());
				
				List<Map<String, Map.Entry<Integer, Integer>>> plagiatAnnotations = 
						mapAnnotationsToOffsets(plagiat.getRawPlagiatText(), plagiatSpans);
				if(plagiatAnnotations==null) continue;
				
				List<Map<String, Map.Entry<Integer, Integer>>> srcAnnotations = 
						mapAnnotationsToOffsets(plagiat.getRawSourceText(), srcSpans);
				if(srcAnnotations==null) continue;

				Collection<String> commonAnnotationKeys = getAnnotationsIntersection(plagiatAnnotations, srcAnnotations);
			
				List<String> plagiatText = joinParagraphsToText(plagiatSpans);
				List<String> srcText = joinParagraphsToText(srcSpans);

//				printExtractedAnnotations(srcText, srcAnnotations);
//				printExtractedAnnotations(plagiatText, plagiatAnnotations);
				
				Map<String, String> plagiats = mapAnnotationsToSentences(plagiatText,
						plagiatAnnotations, new ArrayList<String>(), commonAnnotationKeys);
				Map<String, String> sources = mapAnnotationsToSentences(srcText,
						srcAnnotations, allSrcSents, commonAnnotationKeys);

				plagiats = sortMapByKey(plagiats);
				sources = sortMapByKey(sources);
				plagiatElements = new ArrayList<String>(plagiats.values());
				srcElements = new ArrayList<String>(sources.values());
				
			} catch (IOException iox) {
				logger.error(iox.getMessage());
				throw iox;
			}
//			setupAndSaveAnnotationItem(srcElements, plagiatElements, plagiat, allSrcSents);
		} 
	}
	
	private List<String> joinParagraphsToText(List<List<String>> paragraphSpans){
		List<String> textParagraphs = new ArrayList<String>();
		for (List<String> textSpans: paragraphSpans){
			String text = " " + String.join(" ", textSpans) + " ";
			textParagraphs.add(text);
		}
		return textParagraphs;
	}
	
	private List<String> filterOutIdenticalSents(String plagiarism, String source){
		List<String> plagSents = new LinkedList<String>(Arrays.asList(sentenceDetector.sentDetect(plagiarism)));
		List<String> srcSents = new LinkedList<String>(Arrays.asList(sentenceDetector.sentDetect(source)));
		List<String> sentsToRemove = new ArrayList<String>();
		
		boolean changed = false;
		for(String sent: plagSents){
			if(srcSents.contains(sent)){
				changed = true;
				sentsToRemove.add(sent);
			}
		}
		List<String> pairs = new ArrayList<>();
		if(changed){
			System.out.println("identical inner sents found.");
			sentsToRemove.forEach(sent -> {
				plagSents.remove(sent);
				srcSents.remove(sent);
			});
			pairs.add(String.join(" ", plagSents));
			pairs.add(String.join(" ", srcSents));
		}
		return pairs;
	}
	
	private void setupAndSaveAnnotationItem(List<String> srcElements, List<String> plagiatElements, Fragment fragment,
			List<String> allSrcSents){
		assert(srcElements.size()==plagiatElements.size());
		for (int i = 0; i < srcElements.size(); i++) {
			if(plagiatElements.get(i).equals(srcElements.get(i))) continue;
			
			String plagSent = plagiatElements.get(i);
			String srcSent = srcElements.get(i);

			List<String> annotationPair = filterOutIdenticalSents(plagSent, srcSent);
			if(!annotationPair.isEmpty()){
				plagSent = annotationPair.get(0);
				srcSent = annotationPair.get(1);
			}
			String fakeSource = postProcessor.generateFakePairs(Arrays.asList(plagSent), Arrays.asList(srcSent),
					allSrcSents).get(0);

			int bowDiff = postProcessor.computeBagOfWordsDifference(plagSent, srcSent);
			float ratio = postProcessor.computeWordRatio(plagSent, srcSent);
			
			Annotation annotation = new Annotation();
			annotation.setUrl(fragment.getUrl());
			
//			System.out.println("plagiat: " + plagSent);
//			System.out.println("src: " + srcSent);
			
			if(addedPairs.add(plagSent + " " + srcSent)){
				annotation.setPlagiatSent(plagSent);
				annotation.setSourceSent(srcSent);
				annotation.setNbWordsRatio(ratio);
				annotation.setFakeSourceSent(fakeSource);
				annotation.setBowDiff(bowDiff);
				annotation.setAnnotationIdentifier(fragment.getFragmentIdentifier() + "_" + Integer.toString(i));
				save(annotation);
			}
		}
	}
	private Map<String,String> mapAnnotationsToSentences(List<String> textParagraphs,
												   List<Map<String, Map.Entry<Integer, Integer>>> annotationOffsets,
												   List<String> allSentences, 
												   Collection<String> commonKeys){
		
		Map<String,String> allAnnotationSentences = new HashMap<>();
		assert(textParagraphs.size()==annotationOffsets.size());
		for(int i=0; i<textParagraphs.size(); i++){
			String text = textParagraphs.get(i);
			String[] sentences = sentenceDetector.sentDetect(text);
			Collections.addAll(allSentences, sentences);
			Span[] sentIndices = sentenceDetector.sentPosDetect(text);

			Map<String, Map.Entry<Integer, Integer>> offsets = annotationOffsets.get(i);
			
			Map<String, Map.Entry<Integer, Integer>> commonAnnotationOffsets = offsets.entrySet().stream()
				.filter(map -> commonKeys.contains(map.getKey()))
				.collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
				
			Map<String, String> annotationSentences = filterSentences(sentences, sentIndices, commonAnnotationOffsets);
			annotationSentences.forEach((key, sent) -> allAnnotationSentences.put(key, sent));
		}
		return allAnnotationSentences;
	}
	
	private Map<String,String> sortMapByKey(Map<String,String> allAnnotationSentences){
		Map<String, String> orderedAnnotations = allAnnotationSentences.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
		return orderedAnnotations;
	}
	
	private Collection<String> getAnnotationsIntersection(
			List<Map<String, Map.Entry<Integer, Integer>>> plagiatAnnotations,
			List<Map<String, Map.Entry<Integer, Integer>>> srcAnnotations){
		
		Set<String> plagKeys = new HashSet<>();
		plagiatAnnotations.stream().forEach(map -> {
					map.keySet().forEach(key -> plagKeys.add(key));
					});
		Set<String> srcKeys = new HashSet<>();
		srcAnnotations.stream().forEach(map -> {
			map.keySet().forEach(key -> srcKeys.add(key));
			});
		
		Collection<String> intersect = srcKeys.stream()
				.filter(plagKeys::contains)
				.collect(Collectors.toList());
		
		return intersect;
	}
	
	public List<Map<String, Map.Entry<Integer, Integer>>> mapAnnotationsToOffsets(String rawHtml, 
															List<List<String>> pSpansText) throws IOException {
		
		Document doc = Jsoup.parse(rawHtml);
		Elements hr = doc.select("hr");
		if(hr.size()!=0) hr.get(0).before("<span class=plagiatEnd></span>");
		
		List<Elements> allPs = new ArrayList<Elements>();
		// first spans are without paragraph tag as parent (e.g. headings)
		List<Element> spansFstParagraph =  doc.select("span").stream()
				.filter(s -> !s.parents().is("p") && !s.className().equals("plagiatEnd") 
						&& s.ownText().trim().length()!=0)
				.collect(Collectors.toList());
		Elements fstParagraph = new Elements(spansFstParagraph);
		allPs.add(fstParagraph);
				
		// extract references which appear after first <hr>-tag
		Elements paragraphs = doc.select("p,hr");
		for(Element elem: paragraphs){
			if(elem.tagName().equals("hr"))
				break;
			else
				allPs.add(elem.select("span"));
		}
		
		List<Map<String, Map.Entry<Integer, Integer>>> annotationOffsets = new ArrayList<>();
		for(Elements p: allPs){
			List<String> textSpans = new ArrayList<String>();
			
			Map<String, Map.Entry<Integer, Integer>> annotationOffsetsMap = extractAnnotationOffsetsAndPlainText(p,
					textSpans);

			pSpansText.add(textSpans);
			annotationOffsets.add(annotationOffsetsMap);
		}
		
		return annotationOffsets;
	}

	private Map<String, Map.Entry<Integer, Integer>> extractAnnotationOffsetsAndPlainText(Elements spans,
										List<String> textSpans) {
		int numChars = 1;
		String previousAnnotationClass = "";
		String previousKey="";
		
		Map<String, Map.Entry<Integer, Integer>> annotations = new HashMap<String, Map.Entry<Integer, Integer>>();
		for (Element span : spans) {
			String text = span.ownText().trim();
			String loweredText = new String(text).toLowerCase();
			int len = text.length();
			if(len==0) continue;
			textSpans.add(text);
			String annotationClass = span.className();
			if (annotationClass.startsWith("fragmark")) {
				if(annotationClass.equals(previousAnnotationClass)){
					annotations.get(previousKey).setValue(numChars+len);
					annotations.put(previousKey+" "+loweredText, annotations.get(previousKey));
					annotations.remove(previousKey);
					previousKey=previousKey+" "+loweredText;
				}else{
					annotations.put(annotationClass+"_"+loweredText,
							new AbstractMap.SimpleEntry<Integer, Integer>(numChars, numChars + len));
					previousKey = annotationClass+"_"+loweredText;
				}
				previousAnnotationClass = annotationClass;
			} else if(annotationClass.equals("plagiatEnd")){
				textSpans.remove(textSpans.size() - 1);
				break;
			}
			numChars += (len + 1);
		}
		return annotations;
	}

	private int mapAnnotationToSentenceIds(Span[] sentIndices, int annotationStart){
		int sentStart = 0;
		int count = sentIndices[sentStart].getEnd();
		while(annotationStart > count && sentStart < sentIndices.length-1){
			count = sentIndices[++sentStart].getEnd();
		}
		return sentStart;
	}
	
	private Map<String, String> filterSentences(String[] sentences, Span[] sentIndices,
										 Map<String, Map.Entry<Integer, Integer>> offsets){
		
		Map<String, String> annotatedSentences = new HashMap<>();
		List<Integer> startIndices = new ArrayList<Integer>();
		List<Integer> endIndices = new ArrayList<Integer>();
		List<String> keys = new ArrayList<>();
		
		offsets.forEach((key,offset) -> {
			int idxStartSent = mapAnnotationToSentenceIds(sentIndices, offset.getKey());
			startIndices.add(idxStartSent);
			int idxEndSent = mapAnnotationToSentenceIds(sentIndices, offset.getValue());
			endIndices.add(idxEndSent);
			keys.add(key);
		});
		
		assert startIndices.size()==endIndices.size();
				
		for(int i=0; i<startIndices.size(); i++){
			StringBuilder builder = new StringBuilder();
			int start = startIndices.get(i);
			while(start <= endIndices.get(i)){
				builder.append(" ");
				builder.append(sentences[start++]);
			}
			String key = keys.get(i);
			annotatedSentences.put(key, builder.toString().trim());
		}
		
		return annotatedSentences;
	}

}
