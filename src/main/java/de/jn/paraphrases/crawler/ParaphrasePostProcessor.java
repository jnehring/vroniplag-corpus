package de.jn.paraphrases.crawler;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

@Component
public class ParaphrasePostProcessor {

	@Value("${openNLP.tokenizer.path}")
	String tokenizerPath;
	
	@Value("${words.min}")
	int wordsMin;
	
	@Value("${words.max}")
	int wordsMax;
	
	static Tokenizer tokenizer; 
	static List<String> punctuations = Arrays.asList(".", ",", ";", ":", "?", "!");
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	@PostConstruct
	public void initTokenizer() throws FileNotFoundException, IOException{
		tokenizer = new TokenizerME(new TokenizerModel(new FileInputStream(tokenizerPath)));
	}
	
	public List<String> tokenizeSentences(String sentence){
		List<String> tokens = Arrays.asList(tokenizer.tokenize(sentence))
											.stream()
											.filter(word -> !punctuations.contains(word))
											.collect(Collectors.toList());
		return tokens;
	}
	
	public int computeBagOfWordsDifference(String plagiarism, String source){

		List<String> plagTokens = this.tokenizeSentences(plagiarism);
		List<String> srcTokens = this.tokenizeSentences(source);
		
		List<String> bowPlag = plagTokens.stream().distinct().collect(Collectors.toList());
		List<String> bowSrc = srcTokens.stream().distinct().collect(Collectors.toList());
		
		int common = bowPlag.stream()
		            .filter(e -> bowSrc.contains(e))
		            .collect(Collectors.toList()).size();
		int wordDiff = bowPlag.size() - common + bowSrc.size() - common;
		
		return wordDiff;
	}
	
	public float computeWordRatio(String plagiarism, String source){
		List<String> plagTokens = this.tokenizeSentences(plagiarism);
		List<String> srcTokens = this.tokenizeSentences(source);
		float wordRatio = (float)plagTokens.size()/srcTokens.size();
		return wordRatio;
	}
	
	public List<String> generateFakePairs(List<String> plagiats, List<String> sources, List<String> allSourceSents){
		List<String> fakeSources = new ArrayList<String>();
		for(int i=0; i<plagiats.size(); i++){
			String srcSent = sources.get(i);
			Collections.shuffle(allSourceSents, new Random());	
			String fakeSrc="";
			for(String fake: allSourceSents){
				int nbWords = this.tokenizeSentences(fake).size();
				if(nbWords<wordsMin || nbWords>wordsMax) continue;
				float ratio = computeWordRatio(plagiats.get(i), fake);
				boolean keepSearching = ratio > 1.5 || 
										ratio < 0.5 ||
										fake.equals(srcSent); // ||
										//fake.length() < 40;
				if(keepSearching) continue;
				else{
					fakeSrc = new String(fake);
					break;
				}
			}
			fakeSources.add(fakeSrc);
		}
		return fakeSources;
	}

}
