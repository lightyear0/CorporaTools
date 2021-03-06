package lv.ailab.lvtb.universalizer.transformator.syntax;

import lv.ailab.lvtb.universalizer.conllu.UDv2Relations;
import lv.ailab.lvtb.universalizer.pml.LvtbRoles;
import lv.ailab.lvtb.universalizer.pml.utils.NodeFieldUtils;
import lv.ailab.lvtb.universalizer.pml.utils.NodeListUtils;
import lv.ailab.lvtb.universalizer.pml.utils.NodeUtils;
import lv.ailab.lvtb.universalizer.utils.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathExpressionException;
import java.util.ArrayList;

/**
 * Logic how to choose substitute for ellipted nodes.
 * Created on 2016-09-02.
 *
 * @author Lauma
 */
public class EllipsisLogic
{
	public static Node newParent (Node aNode, DepRelLogic drLogic, Logger logger)
	throws XPathExpressionException
	{
		// This method should not be used for transforming phrase nodes or nodes
		// with morphology.
		if (NodeUtils.getPhraseNode(aNode) != null || NodeUtils.getMNode(aNode) != null)
			return null;

		NodeList children = NodeUtils.getPMLNodeChildren(aNode);
		if (children == null) return null;

		ArrayList<Node> sortedChildren = NodeListUtils.asOrderedList(children);
		String lvtbEffRole = NodeFieldUtils.getEffectiveLabel(aNode);
		String lvtbTag = NodeFieldUtils.getTag(aNode);
		
		// Rules for specific parents.
		if (LvtbRoles.PRED.equals(lvtbEffRole) || lvtbTag.matches("v..[^pn].*")
				|| LvtbRoles.SPC.equals(lvtbEffRole) && lvtbTag.matches("v..(n|p[up]).*"))
		{
			// In case of reduced predicte, search if there is an aux or cop.
			if (LvtbRoles.PRED.equals(lvtbEffRole) || lvtbTag.matches("v..[^pn].*"))
				for (Node n : sortedChildren)
			{
				UDv2Relations noRedUDrole = drLogic.depToUDLogic(
						n, NodeUtils.getPMLParent(n), NodeFieldUtils.getRole(n)).first;
				if (noRedUDrole == null)
					throw new IllegalStateException(
							"Could not determine potential UD role during ellipsis processing for " + NodeFieldUtils
									.getId(n));
				if (noRedUDrole.equals(UDv2Relations.AUX) || noRedUDrole.equals(UDv2Relations.COP))
					return n;
			}

			// Taken from UDv2 guidelines.
			UDv2Relations[] priorities = new UDv2Relations[] {
					UDv2Relations.NSUBJ, UDv2Relations.NSUBJ_PASS,
					UDv2Relations.OBJ, UDv2Relations.IOBJ, UDv2Relations.OBL,
					UDv2Relations.ADVMOD, UDv2Relations.CSUBJ,
					UDv2Relations.CSUBJ_PASS, UDv2Relations.XCOMP,
					UDv2Relations.CCOMP, UDv2Relations.ADVCL};
			for (UDv2Relations role : priorities) for (Node n : sortedChildren)
			{
				UDv2Relations noRedUDrole = drLogic.depToUDLogic(
						n, NodeUtils.getPMLParent(n), NodeFieldUtils.getRole(n)).first;
				if (noRedUDrole == null)
					throw new IllegalStateException(
							"Could not determine potential UD role during ellipsis processing for " + NodeFieldUtils.getId(n));
				if (noRedUDrole.equals(role)) return n;
			}
		}

		// Rules for specific sequences.
		if (children.getLength() > 1)
		{
			// Taken from UDv2 guidelines.
			UDv2Relations[] priorities = new UDv2Relations[] {
					UDv2Relations.AMOD, UDv2Relations.NUMMOD, UDv2Relations.DET,
					UDv2Relations.NMOD, UDv2Relations.CASE};
			for (UDv2Relations role : priorities) for (Node n : sortedChildren)
			{
				UDv2Relations noRedUDrole = drLogic.depToUDLogic(
						n, NodeUtils.getPMLParent(n), NodeFieldUtils.getRole(n)).first;
				if (noRedUDrole == null)
					throw new IllegalStateException(
							"Could not determine potential UD role during ellipsis processing for " + NodeFieldUtils.getId(n));
				if (noRedUDrole.equals(role)) return n;
			}
		}

		// Rules for parents with only one child.
		if (children.getLength() == 1)
			return children.item(0);

		return null;
	}
}
