#!C:\strawberry\perl\bin\perl -w
package LvCorporaTools::FormatTransf::DepPml2Conll;

use strict;
use warnings;

use Exporter();
our @ISA = qw(Exporter);
our @EXPORT_OK = qw(transformFile processDir SPACE_SUBST POSTAG CPOSTAG);

use Data::Dumper;
use File::Path;
use IO::File;
use IO::Dir;
use XML::LibXML;  # XML handling library

use LvCorporaTools::TagTransf::TagPurifier qw(purifyKamolsTag);
use LvCorporaTools::TagTransf::Tag2FeatureList qw(parseTagSet decodeTag);
use LvCorporaTools::GenericUtils::UIWrapper;


###############################################################################
# This program transforms Latvian Treebank files in dependency-only form from
# knited-in PML to CoNLL format. Inputfiles must containtain no empty nodes
# except the root node for each tree. Invalid features like multiple ords per
# single node are not checked.
#
# Works with A level schema v.2.14.
# Input files - utf8.
#
# Developed on Strawberry Perl 5.12.3.0
# Latvian Treebank project, 2012-2013
# Lauma Pretkalnina, LUMII, AILab, lauma@ailab.lv
# Licenced under GPL.
###############################################################################

# Global variables - transformation settings.
# If lemma or token contains space, it will be replaced with this string.
our $SPACE_SUBST = "_";
# What postag to use?
our $POSTAG = 'FULL';		# All SemTi-Kamols tagset features, this is default.
#our $POSTAG = 'PURIFY';	# No lexical features included.
# What cpostag to use?
our $CPOSTAG = 'NONE';		# No CPOSTAG, this is default.
#our $CPOSTAG = 'FIRST';	# CPOSTAG is POS.
#our $CPOSTAG = 'PURIFY';	# No lexical features included in CPOSTAG.

# Process all .pml and .xml files in given folder. This can be used as entry
# point, if this module is used standalone.
sub processDir
{
	autoflush STDOUT 1;
	if (not @_ or @_ < 2)
	{
		print <<END;
Script for batch transfoming dependency-only Latvian Treebank files from
knited-in PML format to CoNLL format. 
Global variables:
   CPOSTAG - CPOSTAG mode: 'PURIFY' (no lexical features) / 'FIRST' (single
             letter) / 'NONE'(no CPOSTAG, default value)
   POSTAG - POSTAG mode 'PURIFY' (no lexical features) / 'FULL' (all
            SemTi-Kamols features, default value)
   SPACE_SUBST - nonspace symbol to substitute spaces that are not allowed in
                 CoNLL formats
Input files should be provided as UTF-8.

Params:
   data directory
   0/1 - print labeled output (if true, trees with reductions will be ommited)
   use CoNLL-2009 format [opt, false by default]

Latvian Treebank project, LUMII, 2012, provided under GPL
END
		exit 1;
	}
	
	my $infix = $_[1] ? 'lab' : 'unlab';
	LvCorporaTools::GenericUtils::UIWrapper::processDir(
		\&transformFile, "^.+\\.(pml|xml)\$", "-$infix.conll", 1, 0, @_);
}

# Process single XML file. This can be used as entry point, if this module is
# used standalone.
sub transformFile
{
	autoflush STDOUT 1;
	if (not @_ or @_ < 3)
	{
		print <<END;
Script for transfoming dependency-only Latvian Treebank files from knited-in
PML format to CoNLL format. 
Global variables:
   CPOSTAG - CPOSTAG mode: 'PURIFY' (no lexical features) / 'FIRST' (single
             letter) / 'NONE'(no CPOSTAG, default value)
   POSTAG - POSTAG mode 'PURIFY' (no lexical features) / 'FULL' (all
            SemTi-Kamols features, default value)
   SPACE_SUBST 
Input files should be provided as UTF-8.

Params:
   directory prefix
   file name
   new file name [opt, current file name used otherwise]
   0/1 - print labeled output - if true, trees with reductions will be ommited
         [opt. false by default]
   use CoNLL-2009 format [opt, false by default]

Latvian Treebank project, LUMII, 2012, provided under GPL
END
		exit 1;
	}
	# Input paramaters.
	my $dirPrefix = shift @_;
	my $oldName = shift @_;
	my $newName = (shift @_ or $oldName);
	my $printLabels = (shift @_ or 0);
	my $conll2009 = (shift @_ or 0);
	
	# Open output file.
	File::Path::mkpath("$dirPrefix/res/");
	my $out = IO::File->new("$dirPrefix/res/$newName", "> :encoding(UTF-8)")
		or die "Could create file $newName: $!";
		
	# Load the tagset XML.
	my $tagDecoder = parseTagSet();
	
	# Load the data XML.
	my $parser = XML::LibXML->new('no_blanks' => 1);
	my $doc = $parser->parse_file("$dirPrefix/$oldName");
	
	my $xpc = XML::LibXML::XPathContext->new();
	$xpc->registerNs('pml', 'http://ufal.mff.cuni.cz/pdt/pml/');
	$xpc->registerNs('fn', 'http://www.w3.org/2005/xpath-functions/');

	# Process each tree.
	foreach my $tree ($xpc->findnodes('/pml:lvadepdata/pml:trees/pml:LM', $doc))
	{
		if ($printLabels and @{$xpc->findnodes('.//pml:reduction', $tree)} gt 0)
		{
			print "Tree ".$tree->find('@id')." has reductions and, thus, will be omitted.\n";
			next;
		}
		my @nodes = sort
			{
				${$xpc->findnodes('pml:ord', $a)}[0]->textContent
				<=> ${$xpc->findnodes('pml:ord', $b)}[0]->textContent
			}
			($xpc->findnodes('.//pml:node[pml:m.rf]', $tree));
		
		# Calculate ID's for CoNLL format.
		my $id = 1;
		my %n2id = ();
		foreach my $n (@nodes)
		{
			$n2id{$n->findvalue('@id')} = $id;
			$id++;
		}

		# Do output in CoNLL format.
		foreach my $n (@nodes)
		{
			my $head = $n->parentNode;
			while (not exists($n2id{$head->findvalue('@id')}) and
				$head->findvalue('@id') ne $tree->findvalue('@id'))
			{
				$head = $head->parentNode;
			}
			print $out "$n2id{$n->findvalue('@id')}\t"; #ID
			my $form = ${$xpc->findnodes('pml:m.rf/pml:form', $n)}[0]->textContent;
			$form =~ s/ /\Q$SPACE_SUBST\E/g;
			print $out "$form\t"; #FORM
			my $lemma = ${$xpc->findnodes('pml:m.rf/pml:lemma', $n)}[0]->textContent;
			$lemma =~ s/ /\Q$SPACE_SUBST\E/g;
			print $out "$lemma\t"; #LEMMA

			if ($conll2009)
			{
				print $out "$lemma\t"; #PLEMMA
			}
			
			my $tag = ${$xpc->findnodes('pml:m.rf/pml:tag', $n)}[0]->textContent;
			$tag =~ tr/][//d;
			$tag =~ s#^N/A$#_#;
			
			if (! $conll2009)
			{
				if ($CPOSTAG eq 'PURIFY')
				{
					print $out purifyKamolsTag($tag); #CPOSTAG
				} elsif ($CPOSTAG eq 'FIRST')
				{
					$tag =~/^(.)/;
					print $out "$1"; #CPOSTAG
				} else
				{
					print $out "_"; #CPOSTAG
				}
				print $out "\t"; #CPOSTAG
			}

			if ($POSTAG eq 'PURIFY')
			{
				print $out purifyKamolsTag($tag); #POSTAG
			} else
			{
				print $out "$tag"; #POSTAG
			}
			print $out "\t"; #POSTAG

			if ($conll2009)
			{
				if ($POSTAG eq 'PURIFY')
				{
					print $out purifyKamolsTag($tag); #PPOS
				} else
				{
					print $out "$tag"; #PPOS
				}
				print $out "\t"; #PPOS
			}
			
			my $decoded = decodeTag($tag, $tagDecoder);
			my $feats = join '|', map{ join '=', @$_} @$decoded;
			$feats =~ s/ /\+/g;
			if (not $feats)
			{
				$feats = '_';
				warn "Tag $tag was not decoded!";
			}
			print $out "$feats\t"; #FEATS
			if ($conll2009)
			{
				print $out "$feats\t"; #PFEATS
			}

			exists($n2id{$head->findvalue('@id')}) ?
				print $out "$n2id{$head->findvalue('@id')}" : print $out "0";	#HEAD
			print $out "\t";
			if ($conll2009)
			{
				exists($n2id{$head->findvalue('@id')}) ?
					print $out "$n2id{$head->findvalue('@id')}" : print $out "0";	#PHEAD
				print $out "\t";
			}

			if ($printLabels)
			{
				print $out ${$xpc->findnodes('pml:role', $n)}[0]->textContent; #DEPREL
			} else
			{
				print $out "_"; #DEPREL
			}
			print $out "\t";
			
			if ($conll2009)
			{
				if ($printLabels)
				{
					print $out ${$xpc->findnodes('pml:role', $n)}[0]->textContent; #PDEPREL
				} else
				{
					print $out "_"; #PDEPREL
				}
				print $out "\t";
			}

			if ($conll2009)
			{
				print $out "_\t_\n"; #FILLPRED, PRED, APRED1-6
			} else 
			{
				print $out "_\t_\n"; #PHEAD, PDEPREL
			}
		}
		print $out "\n";
	}
	
	print "Processing $oldName finished!\n";
}

1;
