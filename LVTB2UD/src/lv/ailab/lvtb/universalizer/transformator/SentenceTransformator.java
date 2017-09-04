package lv.ailab.lvtb.universalizer.transformator;

import lv.ailab.lvtb.universalizer.conllu.Token;
import lv.ailab.lvtb.universalizer.conllu.UDv2Relations;
import lv.ailab.lvtb.universalizer.conllu.UDv2PosTag;
import lv.ailab.lvtb.universalizer.transformator.morpho.FeatsLogic;
import lv.ailab.lvtb.universalizer.transformator.morpho.PosLogic;
import lv.ailab.lvtb.universalizer.pml.Utils;
import lv.ailab.lvtb.universalizer.transformator.syntax.DepRelLogic;
import lv.ailab.lvtb.universalizer.transformator.syntax.EllipsisLogic;
import lv.ailab.lvtb.universalizer.transformator.syntax.PhraseTransformator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Logic for transforming LVTB sentence annotations to UD.
 * No change is done in PML tree, all results are stored in CoNLL-U table only.
 * Assumes normalized ord values (only morpho tokens are numbered).
 * TODO: switch to full ord values?
 * XPathExpressionException everywhere, because all the navigation in the XML is
 * done with XPaths.
 * Created on 2016-04-17.
 *
 * @author Lauma
 */
public class SentenceTransformator
{
	public Sentence s;
	/**
	 * Indication that transformation has failed and the obtained conll data is
	 * garbage.
	 */
	public boolean hasFailed;
	protected PhraseTransformator pTransf;
	protected DepRelLogic drLogic;
	protected PrintWriter warnOut;
	public static boolean DEBUG = false;
	public static boolean WARN_ELLIPSIS = false;
	public static boolean WARN_OMISSIONS = true;
	/**
	 * For already processed nodes without tag set the phrase tag based on node
	 * chosen as substructure root.
	 */
	public static boolean INDUCE_PHRASE_TAGS = true;

	public SentenceTransformator(Node pmlTree, PrintWriter warnOut)
			throws XPathExpressionException
	{
		s = new Sentence(pmlTree);
		hasFailed = false;
		pTransf = new PhraseTransformator(s, warnOut);
		drLogic = new DepRelLogic();
		this.warnOut = warnOut;
	}

	/**
	 * Create CoNLL-U token table, try to fill it in as much as possible.
	 * @return	true, if tree has no untranformable ellipsis; false if tree
	 * 			contains untransformable ellipsis and, thus, result data
	 * 		    has garbage syntax.
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	public boolean transform() throws XPathExpressionException
	{
		if (DEBUG) System.out.printf("Working on sentence \"%s\".\n", s.id);

		transformTokens();
		warnOut.flush();
		extractSendenceText();
		warnOut.flush();
		boolean noMoreEllipsis = preprocessEllipsis();
		if (WARN_ELLIPSIS && !noMoreEllipsis)
			System.out.printf("Sentence \"%s\" has non-trivial ellipsis.\n", s.id);
		transformSyntax();
		warnOut.flush();
		return !hasFailed;
	}

	/**
	 * Utility method for "doing everything": create transformer object,
	 * transform given PML tree and get the string representation for the
	 * resulting CoNLL-U table.
	 * @param pmlTree	tree to transform
	 * @return 	UD tree in CoNLL-U format or null if tree could not be
	 * 			transformed.
	 */
	public static String treeToConll(Node pmlTree, PrintWriter warnOut)
	{
		String id ="<unknown>";
		try {
			SentenceTransformator t = new SentenceTransformator(pmlTree, warnOut);
			id = t.s.id;
			boolean res = t.transform();
			if (res) return t.s.toConllU();
			if (WARN_OMISSIONS)
				warnOut.printf("Sentence \"%s\" is being omitted.\n", t.s.id);
		} catch (NullPointerException|IllegalArgumentException e)
		{
			warnOut.println("Transforming sentence " + id + " completely failed! Check structure and try again.");
			System.err.println("Transforming sentence " + id + " completely failed! Check structure and try again.");
			e.printStackTrace(warnOut);
			e.printStackTrace();
			//throw e;
		}
		catch (XPathExpressionException|IllegalStateException e)
		{
			warnOut.println("Transforming sentence " + id + " completely failed! Might be algorithmic error.");
			System.err.println("Transforming sentence " + id + " completely failed! Might be algorithmic error.");
			e.printStackTrace(warnOut);
			e.printStackTrace();
			//throw new RuntimeException(e);
		}
		return null;
	}

	/**
	 * Create CoNLL-U token table, fill in ID, FORM, LEMMA, XPOSTAG, UPOSTAG and
	 * FEATS fields.
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	public void transformTokens() throws XPathExpressionException
	{
		// Selects ord numbers from the tree.
		NodeList ordNodes = (NodeList)XPathEngine.get().evaluate(".//node[m.rf]/ord",
				s.pmlTree, XPathConstants.NODESET);
		List<Integer> ords = new ArrayList<>();
		for (int i = 0; i < ordNodes.getLength(); i++)
		{
			String ordText = ordNodes.item(i).getTextContent();
			if (ordText != null && ordText.trim().length() > 0)
				ords.add(Integer.parseInt(ordText.trim()));
		}
		ords = ords.stream().sorted().collect(Collectors.toList());
		// Finds all nodes and makes CoNLL-U tokens from them.
		int offset = 0;
		String prevMId = null;
		for (int currentOrd : ords)
		{
			if (currentOrd < 1) continue;

			// Find the m node to be processed.
			NodeList nodes = (NodeList)XPathEngine.get().evaluate(".//node[m.rf and ord=" + currentOrd + "]",
					s.pmlTree, XPathConstants.NODESET);
			if (nodes.getLength() > 1)
				warnOut.printf("\"%s\" has several nodes with ord \"%s\", only first used!\n",
						s.id, currentOrd);

			// Determine, if paragraph has border before this token.
			boolean paragraphChange = false;
			String mId = Utils.getMId(nodes.item(0));
			if (mId.matches("m-.*-p\\d+s\\d+w\\d+"))
				mId = mId.substring(mId.indexOf("-") + 1, mId.lastIndexOf("s"));
			else warnOut.println(
					"Node id \"" + mId + "\"does not match paragraph searching pattern!");
			if (prevMId!= null && !prevMId.equals(mId))
				paragraphChange = true;

			// Make new token.
			offset = transformCurrentToken(nodes.item(0), offset, paragraphChange);

			prevMId = mId;
		}
	}

	/**
	 * Currently extracted from pre-made CoNLL table.
	 * TODO: use PML tree instead?
	 */
	public void extractSendenceText()
	{
		s.text = "";
		for (Token t : s.conll)
		{
			s.text = s.text + t.form;
			if (t.misc == null || !t.misc.matches(".*?\\bSpaceAfter=No\\b.*"))
				s.text = s.text + " ";
		}
		s.text = s.text.trim();
	}



	/**
	 * Helper method: Create CoNLL-U table entry for one token, fill in ID,
	 * FORM, LEMMA, XPOSTAG, UPOSTAG and FEATS fields.
	 * @param aNode		PML A-level node for which CoNLL entry must be created.
	 * @param offset	Difference between PML node's ord value and ID value for
	 *                  CoNLL token to be created.
	 * @param paragraphChange	paragraph border detected right before this
	 *                          token.
	 * @return Offset for next token.
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	protected int transformCurrentToken(Node aNode, int offset, boolean paragraphChange)
	throws XPathExpressionException
	{
		Node mNode = (Node)XPathEngine.get().evaluate("./m.rf[1]",
				aNode, XPathConstants.NODE);
		String mForm = XPathEngine.get().evaluate("./form", mNode);
		String mLemma = XPathEngine.get().evaluate("./lemma", mNode);
		String lvtbTag = XPathEngine.get().evaluate("./tag", mNode);
		boolean noSpaceAfter = false;
		if ("1".equals(XPathEngine.get().evaluate(
				"./w.rf/no_space_after|./w.rf/LM[last()]/no_space_after", mNode)))
			noSpaceAfter = true;

		// Starting from UD v2 numbers and certain abbrieavations are allowed to
		// be tokens with spaces.
		if ((mForm.contains(" ") || mLemma.contains(" ")) &&
				!lvtbTag.matches("x[no].*") &&
				!mForm.replace(" ", "").matches("u\\.t\\.jpr\\.|u\\.c\\.|u\\.tml\\.|v\\.tml\\."))
		{
			int baseOrd = Utils.getOrd(aNode);
			if (baseOrd < 1)
				throw new IllegalArgumentException("Node " + Utils.getId(aNode) + "has no ord value");

			String[] forms = mForm.split(" ");
			String[] lemmas = mLemma.split(" ");
			if (forms.length != lemmas.length)
				warnOut.printf("\"%s\" form \"%s\" do not match \"%s\" on spaces!\n",
						s.id, mForm, mLemma);

			// First one is different.
			Token firstTok = new Token(baseOrd + offset, forms[0],
					lemmas[0], getXpostag(lvtbTag, "_SPLIT_FIRST"));
			if (lvtbTag.matches("xf.*"))
			{
				warnOut.printf("Processing unsplit xf \"%s\", check in treebank!", mForm);
				firstTok.upostag = PosLogic.getUPosTag(firstTok.lemma, firstTok.xpostag, aNode, warnOut);
				firstTok.feats = FeatsLogic.getUFeats(firstTok.form, firstTok.lemma, firstTok.xpostag, aNode, warnOut);
			}
			else if (lvtbTag.matches("x[ux].*"))
			{
				firstTok.upostag = PosLogic.getUPosTag(firstTok.lemma, firstTok.xpostag, aNode, warnOut);
				firstTok.feats = FeatsLogic.getUFeats(firstTok.form, firstTok.lemma, firstTok.xpostag, aNode, warnOut);
			}
			else
			{
				firstTok.upostag = UDv2PosTag.PART;
				firstTok.feats = FeatsLogic.getUFeats(firstTok.form, firstTok.lemma, "qs", aNode, warnOut);
			}
			if (paragraphChange) firstTok.misc = "NewPar=Yes";
			s.conll.add(firstTok);
			s.pmlaToConll.put(Utils.getId(aNode), firstTok);

			// The rest
			for (int i = 1; i < forms.length && i < lemmas.length; i++)
			{
				offset++;
				Token nextTok = new Token(baseOrd + offset, forms[i],
						lemmas[i], getXpostag(lvtbTag, "_SPLIT_PART"));
				if (i == forms.length - 1 || i == lemmas.length - 1 || lvtbTag.matches("x.*"))
				{
					nextTok.upostag = PosLogic.getUPosTag(nextTok.lemma, nextTok.xpostag, aNode, warnOut);
					nextTok.feats = FeatsLogic.getUFeats(nextTok.form, nextTok.lemma, nextTok.xpostag, aNode, warnOut);
				}
				else
				{
					nextTok.upostag = UDv2PosTag.PART;
					nextTok.feats = FeatsLogic.getUFeats(nextTok.form, nextTok.lemma, "qs", aNode, warnOut);
				}
				nextTok.head = firstTok.getFirstColumn();
				if ((i == forms.length - 1 || i == lemmas.length - 1) && noSpaceAfter)
					nextTok.misc = "SpaceAfter=No";
				if (lvtbTag.matches("xf.*")) nextTok.deprel = UDv2Relations.FLAT_FOREIGN;
				else if (lvtbTag.matches("x[ux].*")) nextTok.deprel = UDv2Relations.GOESWITH;
				else nextTok.deprel = UDv2Relations.FIXED;
				s.conll.add(nextTok);
			}
			// TODO Is reasonable fallback for unequal space count in lemma and form needed?
		} else
		{
			Token nextTok = new Token(
					Utils.getOrd(aNode) + offset, mForm, mLemma,
					getXpostag(XPathEngine.get().evaluate("./tag", mNode), null));
			nextTok.upostag = PosLogic.getUPosTag(nextTok.lemma, nextTok.xpostag, aNode, warnOut);
			nextTok.feats = FeatsLogic.getUFeats(nextTok.form, nextTok.lemma, nextTok.xpostag, aNode, warnOut);
			if (noSpaceAfter && paragraphChange)
				 nextTok.misc = "NewPar=Yes|SpaceAfter=No";
			else if (noSpaceAfter)
				nextTok.misc = "SpaceAfter=No";
			else if (paragraphChange)
				nextTok.misc = "NewPar=Yes";
			s.conll.add(nextTok);
			s.pmlaToConll.put(Utils.getId(aNode), nextTok);
		}
		return offset;
	}

	/**
	 * Logic for obtaining XPOSTAG from tag given in LVTB.
	 * @param lvtbTag	tag given in LVTB
	 * @param ending	postfix to be added to the tag
	 * @return XPOSTAG or _ if tag from LVTB is not meaningfull
	 */
	public static String getXpostag (String lvtbTag, String ending)
	{
		if (lvtbTag == null || lvtbTag.length() < 1 || lvtbTag.matches("N/[Aa]"))
			return "_";
		if (ending == null || ending.length() < 1) return lvtbTag;
		else return lvtbTag + ending;
	}

	/**
	 * Remove the ellipsis nodes that can be ignored in latter processing.
	 * @return	 true if all ellipsis was removed
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	public boolean preprocessEllipsis() throws XPathExpressionException
	{
		// Childless, empty reductions are removed.
		NodeList ellipsisChildren = (NodeList) XPathEngine.get().evaluate(
				".//node[reduction and not(m.rf) and not(children)]", s.pmlTree, XPathConstants.NODESET);
		if (ellipsisChildren != null) for (int i = 0; i < ellipsisChildren.getLength(); i++)
		{
			Node current = ellipsisChildren.item(i);
			//Node morpho = Utils.getMNode(current);
			//Node phraseChild = Utils.getPhraseNode(current);
			//NodeList children = Utils.getAllPMLChildren(current);
			//if (morpho == null && phraseChild == null &&
			//		(children == null || children.getLength() < 1))
				current.getParentNode().removeChild(current);
		}

		// Check if there is other reductions.
		ellipsisChildren = (NodeList) XPathEngine.get().evaluate(
				".//node[reduction and not(m.rf)]", s.pmlTree, XPathConstants.NODESET);
		if (ellipsisChildren != null && ellipsisChildren.getLength() > 0) return false;
		/*for (int i = 0; i < ellipsisChildren.getLength(); i++)
		{
					NodeList morpho = (NodeList) XPathEngine.get().evaluate(
							"../../m.rf", ellipsisChildren.item(i), XPathConstants.NODESET);
			if (morpho == null || morpho.getLength() < 1) return false;
		}*/

		return true;
	}

	/**
	 * Fill in DEPREL and HEAD fields in CoNLL-U table.
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	public void transformSyntax() throws XPathExpressionException
	{
		Node pmlPmc = (Node)XPathEngine.get().evaluate(
				"./children/pmcinfo", s.pmlTree, XPathConstants.NODE);
		transformDepSubtrees(s.pmlTree);
		if (hasFailed) return;
		transformPhraseParts(pmlPmc);
		if (hasFailed) return;

		Node newRoot = pTransf.anyPhraseToUD(pmlPmc);
		if (newRoot == null)
			throw new IllegalArgumentException("Sentence " + s.id +" has no root PMC.");
		Token conllRoot = s.pmlaToConll.get(Utils.getId(newRoot));
		s.pmlaToConll.put(Utils.getId(s.pmlTree), conllRoot);
		conllRoot.head = "0";
		conllRoot.deprel = UDv2Relations.ROOT;
		relinkDependents(s.pmlTree, newRoot);
	}

	/**
	 * Helper method: fill in DEPREL and HEAD fields in CoNLL-U table for given
	 * subtree.
	 * @param aNode	root of the subtree to process
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	protected void transformSubtree (Node aNode) throws XPathExpressionException
	{
		if (hasFailed) return;
		if (DEBUG) System.out.printf("Working on node \"%s\".\n", Utils.getId(aNode));

		NodeList children = Utils.getAllPMLChildren(aNode);
		if (children == null || children.getLength() < 1) return;

		transformDepSubtrees(aNode);
		if (hasFailed) return;

		Node newRoot = aNode;
		// Valid LVTB PMLs have no more than one type of phrase - pmc, x or coord.
		Node phraseNode = Utils.getPhraseNode(aNode);

		//// Process phrase overlords.
		if (phraseNode != null)
		{
			transformPhraseParts(phraseNode);
			if (hasFailed) return;
			newRoot = pTransf.anyPhraseToUD(phraseNode);
			if (newRoot == null)
				throw new IllegalStateException(
						"Algorithmic error: phrase transformation returned \"null\" root in sentence " + s.id);

			if (INDUCE_PHRASE_TAGS)
			{
				String phraseTag = Utils.getTag(aNode);
				String newRootTag = Utils.getTag(newRoot);
				if ((phraseTag == null || phraseTag.length() < 1 || phraseTag.matches("N/[Aa]")) &&
						newRootTag != null && newRootTag.length() > 0)
				{
					String type = phraseNode.getNodeName();
					if (type.equals("xinfo") || type.equals("coordinfo"))
					{
						Node tag = (Node)XPathEngine.get().evaluate("./tag", phraseNode, XPathConstants.NODE);
						if (tag == null) tag = phraseNode.getOwnerDocument().createElement("tag");
						while (tag.getFirstChild() != null)
							tag.removeChild(tag.getFirstChild());
						tag.appendChild(phraseNode.getOwnerDocument().createTextNode(newRootTag + "[INDUCED]"));
						phraseNode.appendChild(tag);
					}
				}
			}
		}
		//// Process reduction nodes.
		else if (Utils.isReductionNode(aNode))
		{
			Node redRoot = EllipsisLogic.newParent(aNode, drLogic, warnOut);
			if (redRoot == null)
			{
				hasFailed = true;
				return;
			}
			newRoot = redRoot;
			transformSubtree(newRoot);
			if (hasFailed) return;
		}

		//// Add information about new subroot in the result structure.
		s.pmlaToConll.put(Utils.getId(aNode), s.pmlaToConll.get(Utils.getId(newRoot)));

		//// Process dependants (except the newRoot).
		relinkDependents(aNode, newRoot);
	}

	/**
	 * Helper method: fill in DEPREL and HEAD fields in CoNLL-U table for PML
	 * dependency children of the given node. If the newRoot is one of the
	 * dependents, then it must be processed before invoking this method.
	 * @param parentANode	node whose dependency children will be processed
	 * @param newRoot		node that will be the root of the coresponding UD
	 *                  	structure
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	protected void relinkDependents(Node parentANode, Node newRoot)
	throws XPathExpressionException
	{
		if (hasFailed) return;
		NodeList pmlDependents = (NodeList)XPathEngine.get().evaluate(
				"./children/node", parentANode, XPathConstants.NODESET);
		Token newRootTok = s.pmlaToConll.get(Utils.getId(newRoot));
		if (pmlDependents != null && pmlDependents.getLength() > 0)
			for (int i = 0; i < pmlDependents.getLength(); i++)
		{
			// This happens in case of ellipsis.
			if (pmlDependents.item(i).isSameNode(newRoot)) continue;
			Token conllTok = s.pmlaToConll.get(Utils.getId(pmlDependents.item(i)));
			conllTok.deprel = drLogic.depToUD(pmlDependents.item(i), warnOut);
			conllTok.head = newRootTok.getFirstColumn();
		}
	}

	/**
	 * Helper method: find all dependency children and process subtrees they are
	 * heads of.
	 * @param parentANode	node whose dependency children will be processed
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	protected void transformDepSubtrees(Node parentANode)
	throws XPathExpressionException
	{
		if (hasFailed) return;
		NodeList pmlDependents = (NodeList)XPathEngine.get().evaluate(
				"./children/node", parentANode, XPathConstants.NODESET);
		if (pmlDependents != null && pmlDependents.getLength() > 0)
			for (int i = 0; i < pmlDependents.getLength(); i++)
			{
				transformSubtree(pmlDependents.item(i));
				if (hasFailed) return;
			}
	}

	/**
	 * Helper method: process subtrees under each part of PML phrase.
	 * @param phraseInfoNode	node whose dependency children will be processed
	 * @throws XPathExpressionException	unsuccessfull XPathevaluation (anywhere
	 * 									in the PML tree) most probably due to
	 * 									algorithmical error.
	 */
	protected void transformPhraseParts(Node phraseInfoNode)
	throws XPathExpressionException
	{
		if (hasFailed) return;
		NodeList parts = (NodeList)XPathEngine.get().evaluate(
				"./children/node", phraseInfoNode, XPathConstants.NODESET);
		if (parts != null && parts.getLength() > 0)
			for (int i = 0; i < parts.getLength(); i++)
			{
				transformSubtree(parts.item(i));
				if (hasFailed) return;
			}
	}

}
