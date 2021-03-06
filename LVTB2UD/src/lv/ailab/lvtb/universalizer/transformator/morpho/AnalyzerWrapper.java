package lv.ailab.lvtb.universalizer.transformator.morpho;

import lv.ailab.lvtb.universalizer.utils.Logger;
import lv.semti.morphology.analyzer.Analyzer;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;

public class AnalyzerWrapper
{
	protected static Analyzer morphoEngineSing;

	public static Analyzer getMorpho() throws Exception
	{
		if (morphoEngineSing == null) morphoEngineSing = new Analyzer();
		morphoEngineSing.enableGuessing = true;
		morphoEngineSing.enableAllGuesses = true;
		return morphoEngineSing;
	}

	public static Wordform getAVPairs(String form, String postag, Logger logger)
	{
		try
		{
			Word analysis = getMorpho().analyze(form);
			String tag = postag.contains("_") ? postag.substring(0, postag.indexOf('_')) : postag;
			return analysis.getMatchingWordform(tag, false);
			//TODO: Kad Pēteris partaisīs iespēju izvadīt complain uz citu plūsmu, ieslēgt atpakaļ.
		} catch (Exception e)
		{
			logger.warnForAnalyzerException(e);
			//warnOut.print("Analyzer failed, probably while reading lexicon:\n" + e.getMessage());
			return null;
		}

	}

	public static String getLemma(String form, String postag, Logger logger)
	{
		try
		{
			Word w = getMorpho().analyze(form);
			Wordform wf = w.getMatchingWordform(postag, false);
			return wf.getValue(AttributeNames.i_Lemma);
			//TODO: Kad Pēteris partaisīs iespēju izvadīt complain uz citu plūsmu, ieslēgt atpakaļ.
		} catch (Exception e)
		{
			logger.warnForAnalyzerException(e);
			//warnOut.print("Analyzer failed, probably while reading lexicon:\n" + e.getMessage());
			return null;
		}
	}
}
