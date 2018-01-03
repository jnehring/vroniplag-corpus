package de.jn.paraphrases.crawler;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

	@PostConstruct
	public void match() throws FileNotFoundException, IOException {
		sentenceDetector = new SentenceDetectorME(new SentenceModel(new FileInputStream(sentenceDetectorPath)));
		
		Iterator<Fragment> itr = fragmentRepository.findAll().iterator();
		while (itr.hasNext()) {
			Fragment plagiat = itr.next();

			if(!(plagiat.getUrl().equals("http://de.vroniplag.wikia.com/wiki/Aaf/Fragment_008_01")))
				continue;
			
			List<String> plagiatElements = null;
			List<String> srcElements = null;
			
			try {
				List<String> plagiatSpans = new ArrayList<String>();
				List<String> srcSpans = new ArrayList<String>();
				System.out.println(plagiat.getUrl());
				
				Map<String, Map.Entry<Integer, Integer>> plagiatAnnotations = 
						mapAnnotationsToOffsets(plagiat.getRawPlagiatText(), plagiatSpans);
				if(plagiatAnnotations==null) continue;
				
				Map<String, Map.Entry<Integer, Integer>>srcAnnotations = 
						mapAnnotationsToOffsets(plagiat.getRawSourceText(), srcSpans);
				if(srcAnnotations==null) continue;
				
				Collection<String> commonAnnotationKeys = getAnnotationsIntersection(plagiatAnnotations,
																					 srcAnnotations);
			
				List<Map.Entry<Integer, Integer>> plagOffsets = new ArrayList<Map.Entry<Integer, Integer>>();
				List<Map.Entry<Integer, Integer>> srcOffsets = new ArrayList<Map.Entry<Integer, Integer>>();
				commonAnnotationKeys.forEach(key -> {plagOffsets.add(plagiatAnnotations.get(key));
													 srcOffsets.add(srcAnnotations.get(key));});

				plagiatElements = mapAnnotationsToSentences(plagiatSpans, plagOffsets);
				srcElements = mapAnnotationsToSentences(srcSpans, srcOffsets);
				
			} catch (IOException iox) {
				logger.error(iox.getMessage());
				throw iox;
			}
			List<Integer> bowDiffs = postProcessor.computeBagOfWordsDifference(plagiatElements, srcElements);
			List<Float> wordRatios = postProcessor.computeWordRatio(plagiatElements, srcElements);
			setupAndSaveAnnotationItem(srcElements, plagiatElements, plagiat, bowDiffs, wordRatios);
		} 
	}
	
	
	private void setupAndSaveAnnotationItem(List<String> srcElements, List<String> plagiatElements,
											Fragment fragment, List<Integer> bowDiffs, List<Float> wordRatios){
		
		for (int i = 0; i < srcElements.size(); i++) {
			
			if(plagiatElements.get(i).equals(srcElements.get(i))) continue;

			Annotation annotation = new Annotation();
			annotation.setUrl(fragment.getUrl());
			
			String plagSent = plagiatElements.get(i);
			String srcSent = srcElements.get(i);
//			System.out.println("plagiat: " + plagSent);
//			System.out.println("src: " + srcSent);
//			System.out.println();
			
			if(addedPairs.add(plagSent + " " + srcSent)){ 
				annotation.setPlagiatSent(plagSent);
				annotation.setSourceSent(srcSent);
				annotation.setNbWordsRatio(wordRatios.get(i));
				annotation.setBowDiff(bowDiffs.get(i));
				annotation.setAnnotationIdentifier(fragment.getFragmentIdentifier() + "_" + Integer.toString(i));
				save(annotation);
			}
		}
	}
	private List<String> mapAnnotationsToSentences(List<String> textSpans,
												   List<Map.Entry<Integer, Integer>> annotationOffsets){
		String text = " " + String.join(" ", textSpans) + " ";
		String[] allSentences = sentenceDetector.sentDetect(text);
		Span[] sentIndices = sentenceDetector.sentPosDetect(text);
		List<String> sentences = filterSentences(allSentences, sentIndices, annotationOffsets);
		return sentences;
	}
	
	private Collection<String> getAnnotationsIntersection(Map<String, Map.Entry<Integer, Integer>> plagiatAnnotations,
			Map<String, Map.Entry<Integer, Integer>> srcAnnotations){
		
		Collection<String> srcKeys = srcAnnotations.keySet();
		Collection<String> plagKeys = plagiatAnnotations.keySet();
		
		Collection<String> intersect = srcKeys.stream()
                .filter(plagKeys::contains)
                .collect(Collectors.toList());
		
		return intersect;
	}
	
	public Map<String, Map.Entry<Integer, Integer>> mapAnnotationsToOffsets(String rawHtml, List<String> textSpans)
			throws IOException {
		Document doc = Jsoup.parse(rawHtml);
		Elements hr = doc.select("hr");
		if(hr.size()!=0) hr.get(0).before("<span class=plagiatEnd>must not be empty.</span>");
		
		Elements spans = doc.select("span");
		Map<String, Map.Entry<Integer, Integer>> annotationOffsetsMap = extractAnnotationOffsetsAndPlainText(spans,
				textSpans);

		return annotationOffsetsMap;
	}

	private Map<String,  Map.Entry<Integer, Integer>> extractAnnotationOffsetsAndPlainText(Elements spans,
			List<String> textSpans) {
		Map<String, Map.Entry<Integer, Integer>> annotations = new HashMap<String, Map.Entry<Integer, Integer>>();
		
		int numChars = 1;
		String previousAnnotationClass = "";
		String previousKey="";
		for (Element span : spans) {
			String text = span.ownText().trim();
			int len = text.length();
			if(len==0) continue;
			textSpans.add(text);
			String annotationClass = span.className();
			if (annotationClass.startsWith("fragmark")) {
				if(annotationClass.equals(previousAnnotationClass)){
					annotations.get(previousKey).setValue(numChars+len);
					annotations.put(previousKey+" "+text, annotations.get(previousKey));
					annotations.remove(previousKey);
					previousKey=previousKey+" "+text;
				}else{
					annotations.put(annotationClass+"_"+text,
							new AbstractMap.SimpleEntry<Integer, Integer>(numChars, numChars + len));
					previousKey = annotationClass+"_"+text;
				}
				previousAnnotationClass = annotationClass;
			} else if(annotationClass.equals("plagiatEnd")){
				textSpans.remove(textSpans.size() - 1);
				break;
			}
			numChars += (len + 1);
		}
//		String text = " " + String.join(" ", textSpans) + " ";
//		annotations.values().forEach(offset -> System.out.println(text.substring(offset.getKey(), offset.getValue())));
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
	
	private List<String> filterSentences(String[] sentences, Span[] sentIndices,
										 List<Map.Entry<Integer, Integer>> offsets) {
		
		List<String> annotatedSentences = new ArrayList<String>();
		List<Integer> startIndices = new ArrayList<Integer>();
		List<Integer> endIndices = new ArrayList<Integer>();

		for(Map.Entry<Integer, Integer> offset : offsets){	
			int idxStartSent = mapAnnotationToSentenceIds(sentIndices, offset.getKey());
			startIndices.add(idxStartSent);
			int idxEndSent = mapAnnotationToSentenceIds(sentIndices, offset.getValue());
			endIndices.add(idxEndSent);
		}
		assert startIndices.size()==endIndices.size();
				
		for(int i=0; i<startIndices.size(); i++){
			StringBuilder builder = new StringBuilder();
			int start = startIndices.get(i);
			while(start <= endIndices.get(i)){
				builder.append(sentences[start++]);
			}
			annotatedSentences.add(builder.toString());
		}
		
		return annotatedSentences;
	}

}
