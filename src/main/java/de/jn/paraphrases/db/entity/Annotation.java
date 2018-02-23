package de.jn.paraphrases.db.entity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Annotation {
	
	@Id
	String annotationIdentifier;
	
	String url;
	
	@Lob
	String plagiatSent;
	
	@Lob
	String sourceSent;
	
	@Lob
	String fakeSourceSent;

	String lang_source;
	String lang_plagiat;

	String type;
	
	Float nbWordsRatio;
	Integer bowDiff;
	

	
   @Override
    public String toString() {
        return super.toString();
    }

}
