package fr.free.rocheteau.jerome.engines;

import java.util.Locale;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.models.PorterStemmer;

public class Stemmer extends JCasAnnotator_ImplBase {

	public static final String PARAM_LANGUAGE = "Language";
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory=true)
	private String language;

	public static final String PARAM_FEATURE = "Feature";
	@ConfigurationParameter(name = PARAM_FEATURE, mandatory=true)
	private String feature;

	public static final String PARAM_UPDATE = "Update";
	@ConfigurationParameter(name = PARAM_UPDATE, mandatory=true)
	private boolean update;
	
	// resources
	private SnowballStemmer stemmer;
	
	private String type;
	private Locale locale;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		try {
			// set the stemmer
			this.locale = new Locale(language);
			this.setStemmer(locale);
			
			// set the feature
			String[] path = feature.split(":");
			if (path.length == 2) {
				this.type = path[0];
				this.feature = path[1];
			}
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}
	
	
	public void setStemmer(String language) {
		String className = "org.tartarus.snowball.models." + language + "Stemmer";
		try {
			Class<?> stemClass = Class.forName(className);
			Object object = stemClass.newInstance();
			if (object instanceof SnowballStemmer) {
				SnowballStemmer stemmer = (SnowballStemmer) object;
				this.stemmer = stemmer;
			} else {
				throw new NullPointerException(className);
			}
		} catch (Exception e) {
			this.stemmer = new PorterStemmer();
		}
	}
	
	private void setStemmer(Locale locale) throws Exception {
		String language = locale.getDisplayLanguage(Locale.ENGLISH);
		this.setStemmer(language);
	}
	
	private Type getType(JCas cas) {
		return cas.getTypeSystem().getType(this.type);
	}
	
	private Feature getFeature(JCas cas,Type type,boolean update) {
		if (update) {
			return type.getFeatureByBaseName(this.feature);
		} else {
			Type ty = this.getType(cas);
			return ty.getFeatureByBaseName(this.feature);
		}
	}
	
	
	@Override
	public void process(JCas cas) throws AnalysisEngineProcessException {
		Type type = this.getType(cas);
		Feature feature = this.getFeature(cas, type, this.update);
		AnnotationIndex<Annotation> index = cas.getAnnotationIndex(type);
		FSIterator<Annotation> iter = index.iterator();
		while (iter.hasNext()) {
			Annotation annotation = iter.next();
			String word = annotation.getCoveredText();
			if (word == null) {
				continue;
			} else {
				this.stemmer.setCurrent(word);
				this.stemmer.stem();
				String stem = this.stemmer.getCurrent().toLowerCase(locale);
				if (this.update) {
					this.update(cas, annotation, feature, stem);
				} else {
					this.create(cas, feature, annotation.getBegin(), annotation.getEnd(), stem);
				}
			}
		}
	}

	private void update(JCas cas, Annotation annotation, Feature feature, String value) {
		annotation.setStringValue(feature,value);
	}
	
	private void create(JCas cas, Feature feature, int begin, int end, String value) {
		Type type = feature.getDomain();
		AnnotationFS annotation = cas.getCas().createAnnotation(type, begin, end);
		annotation.setStringValue(feature,value);
		cas.addFsToIndexes(annotation);
	}
	
}
