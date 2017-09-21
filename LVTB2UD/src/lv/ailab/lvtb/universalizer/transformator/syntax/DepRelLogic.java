package lv.ailab.lvtb.universalizer.transformator.syntax;

import lv.ailab.lvtb.universalizer.conllu.UDv2Relations;
import lv.ailab.lvtb.universalizer.pml.*;
import lv.ailab.lvtb.universalizer.util.XPathEngine;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.PrintWriter;
import java.util.HashSet;

/**
 * Relations between dependency labeling used in LVTB and UD.
 * Created on 2016-04-20.
 *
 * @author Lauma
 */
public class DepRelLogic
{
	/**
	 * To avoid repetitive messages, any message once printed are remembered.
	 * Set this to null to avoid this.
	 */
	public HashSet<String> warnRegister;

	protected static DepRelLogic singleton = null;
	protected DepRelLogic() { warnRegister = new HashSet<>(); }

	public static DepRelLogic getSingleton()
	{
		if (singleton == null) singleton = new DepRelLogic();
		return singleton;
	}

	/**
	 * Generic relation between LVTB dependency roles and UD DEPREL.
	 * @param aNode		node for which UD DEPREL should be obtained
	 * @param enhanced  true, if role for enhanced dependency tree is needed
	 * @param warnOut	where all warnings goes
	 * @return	UD DEPREL (including orphan, if parent is reduction and node is
	 * 			representing a core argument).
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	public UDv2Relations depToUD(Node aNode, boolean enhanced, PrintWriter warnOut)
			throws XPathExpressionException
	{
		return depToUD(aNode, aNode, enhanced, warnOut);
	}

	/**
	 * Generic relation between LVTB dependency roles and UD DEPREL.
	 * @param etalonNode	node for which UD DEPREL should be obtained (use
	 *                      this node's placement and role)
	 * @param actualNode	node for which UD DEPREL should be obtained (use
	 *                      this node's tag and lemma)
	 * @param enhanced  true, if role for enhanced dependency tree is needed
	 * @param warnOut	where all warnings goes
	 * @return	UD DEPREL (including orphan, if parent is reduction and node is
	 * 			representing a core argument).
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	public UDv2Relations depToUD(Node etalonNode, Node actualNode,
								 boolean enhanced, PrintWriter warnOut)
	throws XPathExpressionException
	{
		UDv2Relations prelaminaryRole = depToUDNoRed(etalonNode, actualNode, warnOut);
		if (prelaminaryRole == UDv2Relations.DEP)
		{
			warnOnRole(etalonNode, actualNode, enhanced, warnOut);
		}
		if (enhanced) return prelaminaryRole;
		Node pmlParent = Utils.getPMLParent(etalonNode);
		Node pmlEffParent = Utils.getEffectiveAncestor(etalonNode);
		if ((Utils.isReductionNode(pmlParent) || Utils.isReductionNode(pmlEffParent))
				&& (prelaminaryRole.equals(UDv2Relations.NSUBJ)
					|| prelaminaryRole.equals(UDv2Relations.NSUBJ_PASS)
					|| prelaminaryRole.equals(UDv2Relations.OBJ)
					|| prelaminaryRole.equals(UDv2Relations.IOBJ)
					|| prelaminaryRole.equals(UDv2Relations.CSUBJ)
					|| prelaminaryRole.equals(UDv2Relations.CSUBJ_PASS)
					|| prelaminaryRole.equals(UDv2Relations.CCOMP)
					|| prelaminaryRole.equals(UDv2Relations.XCOMP)))
			return UDv2Relations.ORPHAN;
		return prelaminaryRole;
	}

	/**
	 * Generic relation between LVTB dependency roles and UD DEPREL. Orphan
	 * roles are not assigned.
	 * @param etalonNode	node for which UD DEPREL should be obtained (use
	 *                      this node's placement and role)
	 * @param actualNode	node for which UD DEPREL should be obtained (use
	 *                      this node's tagg and lemma)
	 * @param warnOut		where all warnings goes
	 * @return	UD DEPREL before reduction postprocessing
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	public UDv2Relations depToUDNoRed(Node etalonNode, Node actualNode, PrintWriter warnOut)
	throws XPathExpressionException
	{
		String lvtbRole = Utils.getRole(etalonNode);

		// Simple dependencies.
		if (lvtbRole.equals(LvtbRoles.SUBJ))
			return subjToUD(etalonNode, actualNode);
		if (lvtbRole.equals(LvtbRoles.OBJ))
			return objToUD(etalonNode, actualNode);
		if (lvtbRole.equals(LvtbRoles.SPC))
			return spcToUD(etalonNode, actualNode, warnOut);
		if (lvtbRole.equals(LvtbRoles.ATTR))
			return attrToUD(etalonNode, actualNode);
		if (lvtbRole.equals(LvtbRoles.ADV) ||
				lvtbRole.equals(LvtbRoles.SIT))
			return advSitToUD(etalonNode, actualNode);
		if (lvtbRole.equals(LvtbRoles.DET))
			return UDv2Relations.OBL;
		if (lvtbRole.equals(LvtbRoles.NO))
			return noToUD(etalonNode, actualNode);

		// Clausal dependencies.
		if (lvtbRole.equals(LvtbRoles.PREDCL))
			return predClToUD(etalonNode, actualNode);
		if (lvtbRole.equals(LvtbRoles.SUBJCL))
			return subjClToUD(etalonNode, actualNode);
		if (lvtbRole.equals(LvtbRoles.OBJCL))
			return UDv2Relations.CCOMP;
		if (lvtbRole.equals(LvtbRoles.ATTRCL))
			return UDv2Relations.ACL;
		if (lvtbRole.equals(LvtbRoles.PLACECL) ||
				lvtbRole.equals(LvtbRoles.TIMECL) ||
				lvtbRole.equals(LvtbRoles.MANCL) ||
				lvtbRole.equals(LvtbRoles.DEGCL) ||
				lvtbRole.equals(LvtbRoles.CAUSCL) ||
				lvtbRole.equals(LvtbRoles.PURPCL) ||
				lvtbRole.equals(LvtbRoles.CONDCL) ||
				lvtbRole.equals(LvtbRoles.CNSECCL) ||
				lvtbRole.equals(LvtbRoles.CNCESCL) ||
				lvtbRole.equals(LvtbRoles.MOTIVCL) ||
				lvtbRole.equals(LvtbRoles.COMPCL) ||
				lvtbRole.equals(LvtbRoles.QUASICL))
			return UDv2Relations.ADVCL;

		// Semi-clausal dependencies.
		if (lvtbRole.equals(LvtbRoles.INS))
			return insToUD(etalonNode, actualNode, warnOut);
		if (lvtbRole.equals(LvtbRoles.DIRSP))
			return UDv2Relations.PARATAXIS;

		return UDv2Relations.DEP;
	}

	public UDv2Relations subjToUD(Node etalonNode, Node actualNode)
	throws XPathExpressionException
	{
		String tag = Utils.getTag(actualNode);
		// Nominal++ subject
		// This procesing is somewhat tricky: it is allowed for nsubj and
		// nsubjpas to be [rci].*, but it is not allowed for nmod.
		if (tag.matches("[nampxy].*|v..pd.*|[rci].*"))
		{
			Node pmlParent = Utils.getPMLParent(etalonNode);
			String parentTag = Utils.getTag(pmlParent);
			String parentEffType = Utils.getEffectiveLabel(pmlParent);
			Node pmlEffAncestor = Utils.getEffectiveAncestor(etalonNode);
			// Hopefully either parent or effective ancestor is tagged as verb
			// or xPred.
			Node parentXChild = Utils.getPhraseNode(pmlParent);
			Node ancXChild = Utils.getPhraseNode(pmlEffAncestor);

			// Parent is predicate
			if (parentEffType.equals(LvtbRoles.PRED))
			{
				// Parent is complex predicate
				if (LvtbXTypes.XPRED.equals(Utils.getPhraseType(parentXChild)) ||
						LvtbXTypes.XPRED.equals(Utils.getPhraseType(ancXChild)))
				{
					if (parentTag.matches("v..[^p].....p.*|v[^\\[]*\\[pas.*")) return UDv2Relations.NSUBJ_PASS;
					if (parentTag.matches("v.*")) return UDv2Relations.NSUBJ;
					String ancestorTag = Utils.getTag(pmlEffAncestor);
					if (ancestorTag.matches("v..[^p].....p.*|v[^\\[]*\\[pas.*")) return UDv2Relations.NSUBJ_PASS;
					if (ancestorTag.matches("v.*")) return UDv2Relations.NSUBJ;

				}
				// Parent is simple predicate
				else
				{
					// TODO: check the data if participles is realy appropriate here.
					if (parentTag.matches("v..[^p].....a.*|v..pd...a.*|v..pu.*|v..n.*"))
					//if (parentTag.matches("v..[^p].....a.*"))
						return UDv2Relations.NSUBJ;
					if (parentTag.matches("v..[^p].....p.*|v..pd...p.*"))
					//if (parentTag.matches("v..[^p].....p.*"))
						return UDv2Relations.NSUBJ_PASS;
					if (parentTag.matches("z.*"))
					{
						String reduction = XPathEngine.get().evaluate(
								"./reduction", pmlParent);
						if (reduction.matches("v..[^p].....a.*|v..pd...a.*|v..pu.*|v..n.*"))
							return UDv2Relations.NSUBJ;
						if (reduction.matches("v..[^p].....p.*|v..pd...p.*"))
							return UDv2Relations.NSUBJ_PASS;
						//if (reduction.matches("v..n.*"))
						//	return  URelations.NMOD;
					}
				}
			}

			// SPC subject, subject subject ("vienam cīnīties ir grūtāk")
			else if ((LvtbRoles.SPC.equals(parentEffType) || LvtbRoles.SUBJ.equals(parentEffType))
					&& !tag.matches("[rci].*]"))
				return UDv2Relations.OBL;

			// Parent is basElem of some phrase
			else if (parentEffType.equals(LvtbRoles.BASELEM))
			{
				// Parent is complex predicate
				if (LvtbXTypes.XPRED.equals(Utils.getPhraseType(parentXChild)) ||
						LvtbXTypes.XPRED.equals(Utils.getPhraseType(ancXChild)))
				{
					if (parentTag.matches("v..[^pn].....p.*|v[^\\[]+\\[pas.*")) return UDv2Relations.NSUBJ_PASS;
					if (parentTag.matches("v..[^pn].....a.*|v[^\\[]+\\[(act|subst|ad[jv]|pronom).*")) return UDv2Relations.NSUBJ;
					String ancestorTag = Utils.getTag(pmlEffAncestor);
					if (ancestorTag.matches("v..[^pn].....p.*|v[^\\[]+\\[pas.*")) return UDv2Relations.NSUBJ_PASS;
					if (ancestorTag.matches("v..[^pn].....a.*|v[^\\[]+\\[(act|subst|ad[jv]|pronom).*")) return UDv2Relations.NSUBJ;
				}
				else if (parentTag.matches("v..[^pn].....a.*"))
						return UDv2Relations.NSUBJ;
				else if (parentTag.matches("v..[^pn].....p.*"))
						return UDv2Relations.NSUBJ_PASS;
				// Infinitive subjects
				else if (parentTag.matches("v..[np].*") && !tag.matches("[rci].*]"))
						return UDv2Relations.OBL;
			}
		}
		// Infinitive
		if (tag.matches("v..n.*"))
			return UDv2Relations.CCOMP;

		return UDv2Relations.DEP;
	}

	public UDv2Relations objToUD(Node etalonNode, Node actualNode)
	throws XPathExpressionException
	{
		String tag = Utils.getTag(actualNode);
		String parentTag = Utils.getTag(Utils.getPMLParent(etalonNode));
		Node phraseChild = Utils.getPhraseNode(actualNode);
		if (phraseChild != null)
		{
			String constLabel = Utils.getAnyLabel(phraseChild);
			if (LvtbXTypes.XPREP.matches(constLabel)) return UDv2Relations.IOBJ;
		}
		if (tag.matches(".*?\\[(pre|post).*]")) return UDv2Relations.IOBJ;
		if (tag.matches("[na]...a.*|[pm]....a.*|v..p...a.*")) return UDv2Relations.OBJ;
		if (tag.matches("[na]...n.*|[pm]....n.*|v..p...n.*") && parentTag.matches("v..d.*"))
			return UDv2Relations.OBJ;
		return UDv2Relations.IOBJ;
	}

	public UDv2Relations spcToUD(Node etalonNode, Node actualNode, PrintWriter warnOut)
	throws XPathExpressionException
	{
		String tag = Utils.getTag(actualNode);
		String parentTag = Utils.getTag(Utils.getPMLParent(etalonNode));

		// If parent is something reduced to punctuation mark, use reduction
		// tag instead.
		if (parentTag.matches("z.*"))
		{
			String parentRed = Utils.getReduction(Utils.getPMLParent(etalonNode));
			if (parentRed != null && parentRed.length() > 0)
				parentTag = parentRed;
		}

		String parentEffRole = Utils.getEffectiveLabel(Utils.getPMLParent(etalonNode));
		// Infinitive SPC
		if (tag.matches("v..n.*"))
		{
			Node pmlEfParent = Utils.getEffectiveAncestor(etalonNode);
			String effParentType = Utils.getAnyLabel(pmlEfParent);
			//if ((effParentType.equals(LvtbRoles.PRED) ||
			//		(effParentType.equals(LvtbRoles.BASELEM) &&
			//		LvtbXTypes.XPRED.equals(Utils.getEffectiveLabel(Utils.getPMLParent(pmlEfParent))))) &&
			//		parentTag.matches("v..([^p]|p[^d]).*"))
			if (parentTag.matches("v..([^p]|p[^d]).*") || LvtbXTypes.XPRED.equals(effParentType))
				return UDv2Relations.CCOMP; // It is impposible safely to distinguish xcomp for now.
			if (parentTag.matches("v..pd.*")) return UDv2Relations.XCOMP;
			if (parentTag.matches("[nampxy].*")) return UDv2Relations.ACL;
		}
		String xType = XPathEngine.get().evaluate("./children/xinfo/xtype", actualNode);
		// prepositional SPC
		if (xType != null && xType.equals(LvtbXTypes.XPREP))
		{
			NodeList preps = (NodeList)XPathEngine.get().evaluate(
					"./children/xinfo/children/node[role='" + LvtbRoles.PREP + "']",
					actualNode, XPathConstants.NODESET);
			NodeList basElems = (NodeList)XPathEngine.get().evaluate(
					"./children/xinfo/children/node[role='" + LvtbRoles.BASELEM + "']",
					actualNode, XPathConstants.NODESET);

			// NB! Secība ir svarīga. Nevar pirms šī likt parastos nomenus!
			if (preps.getLength() > 1)
				warn(String.format("\"%s\" with ID \"%s\" has multiple \"%s\".\n",
						xType, Utils.getId(actualNode), LvtbRoles.PREP), warnOut);
			if (basElems.getLength() > 1)
				warn(String.format("\"%s\" with ID \"%s\" has multiple \"%s\".\n",
						xType, Utils.getId(actualNode), LvtbRoles.BASELEM), warnOut);
			String baseElemTag = Utils.getTag(basElems.item(0));
			if ("par".equals(Utils.getLemma(preps.item(0)))
					&& baseElemTag != null && baseElemTag.matches("[nampxy].*")
					&& (parentTag.matches("v.*") || LvtbRoles.PRED.equals(parentEffRole)))
				return UDv2Relations.XCOMP;
			else if (parentTag.matches("[nampxy].*|v..pd.*"))
				return UDv2Relations.NMOD;
		}

		// Simple nominal SPC
		if (tag.matches("[na]...[g].*|[pm]....[g].*|v..p...[g].*") ||
			tag.matches("x.*|y.*") && parentTag.matches("v..p....ps.*"))
			return UDv2Relations.OBL;
		if (tag.matches("[na]...[adnl].*|[pm]....[adnl].*|v..p...[adnl].*|x.*|y.*"))
			return UDv2Relations.ACL;

		// SPC with comparison
		if (xType != null && xType.equals(LvtbXTypes.XSIMILE)) return UDv2Relations.ADVCL;

		// Participal SPC
		if (tag.matches("v..p[pu].*")) return UDv2Relations.ADVCL;

		// SPC with punctuation.
		String pmcType = XPathEngine.get().evaluate("./children/pmcinfo/pmctype", actualNode);
		if (pmcType != null && pmcType.equals(LvtbPmcTypes.SPCPMC))
		{
			NodeList basElems = (NodeList)XPathEngine.get().evaluate(
					"./children/pmcinfo/children/node[role='" + LvtbRoles.BASELEM + "']",
					actualNode, XPathConstants.NODESET);
			if (basElems.getLength() > 1)
				warn(String.format("\"%s\" has multiple \"%s\"", pmcType, LvtbRoles.BASELEM), warnOut);
			String basElemTag = Utils.getTag(basElems.item(0));
			String basElemXType = Utils.getPhraseType(basElems.item(0));

			// SPC with comparison
			if (LvtbXTypes.XSIMILE.equals(basElemXType)) return UDv2Relations.ADVCL;
			// Participal SPC, adverbs in commas
			if (basElemTag.matches("v..p[pu].*|r.*")) return UDv2Relations.ADVCL;
			// Nominal SPC
			if (basElemTag.matches("n.*") ||
					basElemTag.matches("y.*") &&
					Utils.getLemma(basElems.item(0)).matches("\\p{Lu}+"))
				return UDv2Relations.APPOS;
			// Adjective SPC
			if (basElemTag.matches("a.*|v..d.*")) return UDv2Relations.ACL;
		}

		return UDv2Relations.DEP;
	}

	public UDv2Relations attrToUD(Node etalonNode, Node actualNode)
	throws XPathExpressionException
	{
		String tag = Utils.getTag(actualNode);
		String lemma = Utils.getLemma(actualNode);

		if (tag.matches("n.*|y.*") || lemma.equals("%")) return UDv2Relations.NMOD;
		if (tag.matches("r.*")) return UDv2Relations.ADVMOD;
		if (tag.matches("m[cf].*|xn.*")) return UDv2Relations.NUMMOD;
		if (tag.matches("mo.*|xo.*|v..p.*")) return UDv2Relations.AMOD;
		if (tag.matches("p.*")) return UDv2Relations.DET;
		if (tag.matches("a.*"))
		{
			if (lemma != null && lemma.matches("(man|mūs|tav|jūs|viņ|sav)ēj(ais|ā)|(daudz|vairāk|daž)(i|as)"))
				return UDv2Relations.DET;
			return UDv2Relations.AMOD;
		}
		// Both cases can provide mistakes, but there is no way to solve this
		// now.
		if (tag.matches("x[fu].*")) return UDv2Relations.NMOD;
		if (tag.matches("xx.*")) return UDv2Relations.AMOD;
		
		/*if (tag.matches("y.*"))
		{
			String lemma = Utils.getLemma(aNode);
			if (lemma.matches("\\p{Lu}+"))
				return URelations.NMOD;
		}*/

		return UDv2Relations.DEP;
	}

	public UDv2Relations advSitToUD(Node etalonNode, Node actualNode)
	throws XPathExpressionException
	{
		String tag = Utils.getTag(actualNode);
		if (tag.matches("mc.*|xn.*"))
			return UDv2Relations.NUMMOD;

		if (tag.matches("n.*|x[fo].*|p.*|.*\\[(pre|post|rel).*|y.*|mo.*"))
			return UDv2Relations.OBL;

		String lemma = Utils.getLemma(actualNode);

		if (tag.matches("r.*") || lemma.equals("%")) return UDv2Relations.ADVMOD;
		if (tag.matches("q.*")) return UDv2Relations.DISCOURSE;

		return UDv2Relations.DEP;
	}

	public UDv2Relations noToUD(Node etalonNode, Node actualNode)
	throws XPathExpressionException
	{
		String tag = Utils.getTag(actualNode);
		String lemma = Utils.getLemma(actualNode);
		String subPmcType = XPathEngine.get().evaluate("./children/pmcinfo/pmctype", actualNode);
		if (LvtbPmcTypes.ADDRESS.equals(subPmcType)) return UDv2Relations.VOCATIVE;
		if (LvtbPmcTypes.INTERJ.equals(subPmcType) || LvtbPmcTypes.PARTICLE.equals(subPmcType))
			return UDv2Relations.DISCOURSE;
		if (tag != null && tag.matches("[qi].*")) return UDv2Relations.DISCOURSE;

		if (lemma.matches("utt\\.|u\\.t\\.jpr\\.|u\\.c\\.|u\\.tml\\.|v\\.tml\\."))
			return UDv2Relations.CONJ;

		return UDv2Relations.DEP;
	}

	public UDv2Relations predClToUD(Node etalonNode, Node actualNode)
	throws XPathExpressionException
	{
		String parentType = Utils.getAnyLabel(Utils.getPMLParent(etalonNode));

		// Parent is simple predicate
		if (parentType.equals(LvtbRoles.PRED)) return UDv2Relations.CCOMP;
		// Parent is complex predicate
		String grandPatentType = Utils.getAnyLabel(Utils.getPMLGrandParent(etalonNode));
		if (grandPatentType.equals(LvtbXTypes.XPRED)) return UDv2Relations.ACL;

		return UDv2Relations.DEP;
	}

	public UDv2Relations subjClToUD(Node etalonNode, Node actualNode)
	throws XPathExpressionException
	{
		Node pmlParent = Utils.getPMLParent(etalonNode);

		// Effective ancestor is predicate
		if (LvtbRoles.PRED.equals(Utils.getEffectiveLabel(pmlParent)))
		{
			String parentTag = Utils.getTag(pmlParent);
			Node pmlEffAncestor = Utils.getEffectiveAncestor(etalonNode);
			// Hopefully either parent or effective ancestor is tagged as verb
			// or xPred.
			Node parentXChild = Utils.getPhraseNode(pmlParent);
			Node ancXChild = Utils.getPhraseNode(pmlEffAncestor);
			// Parent is complex predicate
			if (LvtbXTypes.XPRED.equals(Utils.getPhraseType(parentXChild)) ||
					LvtbXTypes.XPRED.equals(Utils.getPhraseType(ancXChild)))
			{
				if (parentTag.matches("v..[^p].....p.*|v.*?\\[pas.*")) return UDv2Relations.CSUBJ_PASS;
				if (parentTag.matches("v.*")) return UDv2Relations.CSUBJ;
				String ancestorTag = Utils.getTag(pmlEffAncestor);
				if (ancestorTag.matches("v..[^p].....p.*|v.*?\\[pas.*")) return UDv2Relations.CSUBJ_PASS;
				if (ancestorTag.matches("v.*")) return UDv2Relations.CSUBJ;
			}
			// Parent is simple predicate
			else
			{
				if (parentTag.matches("v..[^p].....a.*|v..n.*"))
					return UDv2Relations.CSUBJ;
				if (parentTag.matches("v..[^p].....p.*"))
					return UDv2Relations.CSUBJ_PASS;
			}
		} else if (LvtbRoles.SUBJ.equals(Utils.getEffectiveLabel(pmlParent)))
			return UDv2Relations.ACL;

		return UDv2Relations.DEP;
	}

	public UDv2Relations insToUD(Node etalonNode, Node actualNode, PrintWriter warnOut)
	throws XPathExpressionException
	{
		NodeList basElems = (NodeList)XPathEngine.get().evaluate(
				"./children/pminfo/children/node[role='" + LvtbRoles.PRED + "']",
				etalonNode, XPathConstants.NODESET);
		if (basElems!= null && basElems.getLength() > 1)
			warn (String.format("\"%s\" has multiple \"%s\"", LvtbPmcTypes.INSPMC, LvtbRoles.PRED),
					warnOut);
		if (basElems != null) return UDv2Relations.PARATAXIS;
		return UDv2Relations.DISCOURSE; // Washington (CNN) is left unidentified.
	}

	/**
	 * Print out the warning that role was not tranformed.
	 * @param etalonNode		node for which UD DEPREL should be obtained
	 *                          (use this node's placement and role)
	 * @param actualNode		node for which UD DEPREL should be obtained
	 *                          (use this node's tagg and lemma)
	 * @param enhanced  true, if role for enhanced dependency tree is being made
	 * @param warnOut	stream where to warn
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	protected void warnOnRole(Node etalonNode, Node actualNode, boolean enhanced,
							  PrintWriter warnOut)
	throws XPathExpressionException
	{
		String etalonNodeId = Utils.getId(etalonNode);
		String actualNodeId = Utils.getId(actualNode);
		String role = XPathEngine.get().evaluate("./role", etalonNode);
		String prefix = enhanced ? "Enhanced role" : "Role";
		String nodeInfo = etalonNode.isSameNode(actualNode) ?
				"node " + etalonNodeId : "node pair " + etalonNodeId + ", " + actualNodeId;
		String warning = String.format("%s \"%s\" for %s was not transformed.",
				prefix, role, nodeInfo);
		warn(warning, warnOut);
	}

	/**
	 * Print out the given warning and add it to the warning register.
	 * @param warning	warning to print
	 * @param warnOut	stream where to print
	 */
	protected void warn(String warning, PrintWriter warnOut)
	{
		if (warnRegister!= null && !warnRegister.contains(warning))
		{
			warnOut.println(warning);
			warnRegister.add(warning);
		}
	}

}
