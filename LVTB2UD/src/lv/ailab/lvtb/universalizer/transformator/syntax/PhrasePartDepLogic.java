package lv.ailab.lvtb.universalizer.transformator.syntax;

import lv.ailab.lvtb.universalizer.conllu.UDv2Relations;
import lv.ailab.lvtb.universalizer.pml.*;
import lv.ailab.lvtb.universalizer.util.Tuple;
import lv.ailab.lvtb.universalizer.util.XPathEngine;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.text.LabelView;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

/**
 * Relation between phrase part names used in LVTB and dependency labeling used
 * in UD (regular only; irregularity processing and for phrase root labeling is
 * done in PhraseTransform).
 * Created on 2016-04-26.
 *
 * @author Lauma
 */
public class PhrasePartDepLogic
{

	/**
	 * Generic relation between phrase part roles and UD DEPREL and/or enhanced
	 * dependencies.
	 * Only for nodes that are not roots or subroots.
	 * NB! Case when a part of crdClauses maps to parataxis is handled in
	 * PhraseTransform class.
	 * Case when a part of unstruct basElem maps to foreign is handled in
	 * PhraseTransform class.
	 * All specific cases with xPred are handled in PhraseTransform class.
	 * @param aNode			node for which the DEPREL must be obtained
	 * @param phraseType	type of phrase in relation to which DEPREL must be
	 *                      chosen
	 * @param warnOut 		where all the warnings goes
	 * @return	UD dependency role and enhanced depency role postfix, if such is
	 * 			needed.
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	public static Tuple<UDv2Relations, String> phrasePartRoleToUD(
			Node aNode, String phraseType, PrintWriter warnOut)
	throws XPathExpressionException
	{
		String nodeId = Utils.getId(aNode);
		String lvtbRole = Utils.getRole(aNode);

		if ((phraseType.equals(LvtbPmcTypes.SENT) ||
				phraseType.equals(LvtbPmcTypes.UTTER) ||
				phraseType.equals(LvtbPmcTypes.SUBRCL)) ||
				phraseType.equals(LvtbPmcTypes.MAINCL) ||
				phraseType.equals(LvtbPmcTypes.INSPMC) ||
				phraseType.equals(LvtbPmcTypes.DIRSPPMC))
			if (lvtbRole.equals(LvtbRoles.NO))
			{
				String subPmcType = XPathEngine.get().evaluate("./children/pmcinfo/pmctype", aNode);
				if (LvtbPmcTypes.ADDRESS.equals(subPmcType))
					return Tuple.of(UDv2Relations.VOCATIVE, null);
				if (LvtbPmcTypes.INTERJ.equals(subPmcType) || LvtbPmcTypes.PARTICLE.equals(subPmcType))
					return Tuple.of(UDv2Relations.DISCOURSE, null);
				String tag = Utils.getTag(aNode);
				if (tag != null && tag.matches("[qi].*"))
					return Tuple.of(UDv2Relations.DISCOURSE, null);
				if (tag != null && tag.matches("n...v.*"))
					return Tuple.of(UDv2Relations.VOCATIVE, null);
			}

		if (phraseType.equals(LvtbPmcTypes.SENT) || phraseType.equals(LvtbPmcTypes.UTTER)
				|| phraseType.equals(LvtbPmcTypes.SUBRCL) || phraseType.equals(LvtbPmcTypes.MAINCL)
				|| phraseType.equals(LvtbPmcTypes.INSPMC) || phraseType.equals(LvtbPmcTypes.SPCPMC)
				|| phraseType.equals(LvtbPmcTypes.DIRSPPMC) || phraseType.equals(LvtbPmcTypes.QUOT)
				|| phraseType.equals(LvtbPmcTypes.ADDRESS) || phraseType.equals(LvtbPmcTypes.INTERJ)
				|| phraseType.equals(LvtbPmcTypes.PARTICLE))
			if (lvtbRole.equals(LvtbRoles.PUNCT))
				return Tuple.of(UDv2Relations.PUNCT, null);

		if (phraseType.equals(LvtbPmcTypes.SENT) ||
				phraseType.equals(LvtbPmcTypes.UTTER) ||
				phraseType.equals(LvtbPmcTypes.MAINCL) ||
				phraseType.equals(LvtbPmcTypes.INSPMC) ||
				phraseType.equals(LvtbPmcTypes.DIRSPPMC))
			if (lvtbRole.equals(LvtbRoles.CONJ))
			{
				String tag = Utils.getTag(aNode);
				if (tag.matches("cc.*"))
					return Tuple.of(UDv2Relations.CC, null);
				if (tag.matches("cs.*"))
					return Tuple.of(UDv2Relations.MARK, null);
			}

		if (phraseType.equals(LvtbPmcTypes.SUBRCL))
			if (lvtbRole.equals(LvtbRoles.CONJ))
				return Tuple.of(UDv2Relations.MARK, null);


		if (phraseType.equals(LvtbCoordTypes.CRDPARTS) || phraseType.equals(LvtbCoordTypes.CRDCLAUSES))
		{
			if (lvtbRole.equals(LvtbRoles.CRDPART))
				return Tuple.of(UDv2Relations.CONJ, null); // Parataxis role is given in PhraseTransform class.
			if (lvtbRole.equals(LvtbRoles.CONJ))
				return Tuple.of(UDv2Relations.CC, null);
			if (lvtbRole.equals(LvtbRoles.PUNCT))
				return Tuple.of(UDv2Relations.PUNCT, null);
		}

		if (phraseType.equals(LvtbXTypes.XAPP) &&
				lvtbRole.equals(LvtbRoles.BASELEM))
			return Tuple.of(UDv2Relations.NMOD, null);
		if ((phraseType.equals(LvtbXTypes.XNUM) ||
				phraseType.equals(LvtbXTypes.COORDANAL)) &&
				lvtbRole.equals(LvtbRoles.BASELEM))
			return Tuple.of(UDv2Relations.COMPOUND, null);
		if ((phraseType.equals(LvtbXTypes.PHRASELEM) ||
				phraseType.equals(LvtbXTypes.UNSTRUCT) ||
				phraseType.equals(LvtbPmcTypes.INTERJ) ||
				phraseType.equals(LvtbPmcTypes.PARTICLE)) &&
				lvtbRole.equals(LvtbRoles.BASELEM))
			return Tuple.of(UDv2Relations.FLAT, null);
		if (phraseType.equals(LvtbXTypes.NAMEDENT) &&
				lvtbRole.equals(LvtbRoles.BASELEM))
			return Tuple.of(UDv2Relations.FLAT_NAME, null);

		if (phraseType.equals(LvtbXTypes.SUBRANAL) &&
				lvtbRole.equals(LvtbRoles.BASELEM))
		{
			// TODO check by parents xTag?
			String subXType = XPathEngine.get().evaluate("./children/xinfo/xtype", aNode);
			String tag = Utils.getTag(aNode);
			if (LvtbXTypes.XPREP.equals(subXType))
			{
				if (tag.matches("[np].*"))
				{
					NodeList preps = (NodeList)XPathEngine.get().evaluate(
							"./children/xinfo/children/node[role='" + LvtbRoles.PREP + "']",
							aNode, XPathConstants.NODESET);
					if (preps.getLength() > 1)
						warnOut.printf("\"%s\" with ID \"%s\" has multiple \"%s\"\n.",
								subXType, Utils.getId(aNode), LvtbRoles.PREP);
					String prepLemma = Utils.getLemma(preps.item(0));
					return Tuple.of(UDv2Relations.NMOD, prepLemma);
				}
				if (tag.matches("(mc|xn).*")) return Tuple.of(UDv2Relations.NUMMOD, null);
				if (tag.matches("(a|ya|xo|mo).*")) return Tuple.of(UDv2Relations.AMOD, null);
			}
			else if (LvtbXTypes.XSIMILE.equals(subXType)) return Tuple.of(UDv2Relations.CASE, null);
			else if (tag.matches("p.*")) return Tuple.of(UDv2Relations.DET, null);
			else if (tag.matches("q.*")) return Tuple.of(UDv2Relations.FLAT, null);

			return Tuple.of(UDv2Relations.COMPOUND, null);
		}

		if (phraseType.equals(LvtbXTypes.XPREP) &&
				lvtbRole.equals(LvtbRoles.PREP))
			return Tuple.of(UDv2Relations.CASE, null);
		if (phraseType.equals(LvtbXTypes.XPARTICLE) &&
				lvtbRole.equals(LvtbRoles.NO))
			return Tuple.of(UDv2Relations.DISCOURSE, null);

		if (phraseType.equals(LvtbXTypes.XSIMILE) &&
				lvtbRole.equals(LvtbRoles.CONJ))
		{
			// For now let us assume, that conjunction can't be coordinated.
			// Then parent in this situation is the xSimile itself.
			Node firstAncestor = Utils.getEffectiveAncestor(Utils.getPMLParent(aNode)); // node/xinfo/pmcinfo/phraseinfo
			Node secondAncestor = Utils.getEffectiveAncestor(firstAncestor); // node/xinfo/pmcinfo/phraseinfo
			String firstAncType = Utils.getAnyLabel(firstAncestor);
			String secondAncType = Utils.getAnyLabel(secondAncestor);

			// Check the specific roles
			if (LvtbRoles.BASELEM.equals(firstAncType))
			{
				if (LvtbPmcTypes.SPCPMC.equals(secondAncType) ||
						LvtbPmcTypes.INSPMC.equals(secondAncType))
					return Tuple.of(UDv2Relations.MARK, null);
				if (LvtbXTypes.XPRED.equals(secondAncType) || LvtbPmcTypes.UTTER.equals(secondAncType))
					return Tuple.of(UDv2Relations.DISCOURSE, null);
			}
			// In generic SPC case use mark, in generic ADV we use discourse.
			if (LvtbRoles.SPC.equals(firstAncType))
				return Tuple.of(UDv2Relations.MARK, null);

			// NO adv + xSimile instances in data! Is this old?
			//if (LvtbRoles.ADV.equals(firstAncType))
			//	return Tuple.of(UDv2Relations.DISCOURSE, null);
			
			Node effAncestor = secondAncestor;
			if (LvtbXTypes.XPARTICLE.equals(Utils.getAnyLabel(effAncestor)))
				effAncestor = Utils.getEffectiveAncestor(effAncestor);
			String effAncLabel = Utils.getAnyLabel(effAncestor);

			if (LvtbRoles.SPC.equals(effAncLabel) || LvtbPmcTypes.SPCPMC.equals(effAncLabel)
					|| LvtbPmcTypes.SPCPMC.equals(effAncLabel))
				return Tuple.of(UDv2Relations.MARK, null);

			// NO adv + xSimile instances in data! Is this old?
			//if (LvtbRoles.ADV.equals(effAncLabel))
			//	return Tuple.of(UDv2Relations.DISCOURSE, null);
		}

		if (phraseType.equals(LvtbXTypes.XPRED))
		{
			if (lvtbRole.equals(LvtbRoles.AUXVERB))
				return Tuple.of(UDv2Relations.AUX, null);
			if (lvtbRole.equals(LvtbRoles.BASELEM) ||
					lvtbRole.equals(LvtbRoles.MOD))
				return Tuple.of(UDv2Relations.XCOMP, null);
		}

		warnOut.printf("\"%s\" (%s) in \"%s\" has no UD label.\n",
				lvtbRole, nodeId, phraseType);
		return Tuple.of(UDv2Relations.DEP, null);
	}
}
