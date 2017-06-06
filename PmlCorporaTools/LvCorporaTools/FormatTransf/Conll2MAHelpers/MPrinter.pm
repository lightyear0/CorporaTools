#!C:\strawberry\perl\bin\perl -w
package LvCorporaTools::FormatTransf::Conll2MAHelpers::MPrinter;

use strict;
use warnings;

use Exporter();
our @ISA = qw(Exporter);
our @EXPORT_OK = qw(printMBegin printMEnd printMSentBegin printMSentEnd printMDataNode);

#TODO rework in proper OOP way.
# Mostly these functions just print stuff in output stream to create PML-M files.

# PML-M file header.
sub printMBegin
{
	my ($output, $docId, $annotationInfo) = @_;
	print $output <<END;
<?xml version="1.0" encoding="utf-8"?>
<lvmdata xmlns="http://ufal.mff.cuni.cz/pdt/pml/">
	<head>
		<schema href="lvmschema.xml" />
		<references>
			<reffile id="w" name="wdata" href="$docId.w" />
		</references>
	</head>
	<meta>
		<lang>lv</lang>
		<annotation_info id="semi-automatic"> $annotationInfo</annotation_info>
	</meta>

END
}

# PML-M file footer.
sub printMEnd
{
	my $output = shift @_;
	print $output <<END;
</lvmdata>
END
}

# PML-M file sentence header.
sub printMSentBegin
{
	my ($output, $sentId) = @_;
	print $output <<END;
	<s id="$sentId">
END
}

# PML-M file sentence footer.
sub printMSentEnd
{
	my $output = shift @_;
	print $output <<END;
	</s>
END
}

# One PML-M data node.
sub printMDataNode
{
	my ($output, $docId, $mId, $wIds, $token, $lemma, $tag) = @_;
	$lemma = 'N/A' unless ($lemma and $lemma !~ /^\s*$/);
	$tag = 'N/A' unless ($tag and $tag !~ /^\s*$/);
	my $wIdString = '';
	if (@$wIds > 1)
	{
		$wIdString = '<LM>w#' . join('</LM><LM>w#', @$wIds) . '</LM>';
	}
	elsif (@$wIds == 1)
	{
		$wIdString = "w#@$wIds[0]";
	}
	print $output <<END;
		<m id="$mId">
			<src.rf>$docId</src.rf>
END
	if ($wIdString)
	{
		print $output <<END;
			<w.rf>$wIdString</w.rf>
END
	}
	if (@$wIds > 1)
	{
		print $output <<END;
			<form_change>union</form_change>
END
	}
	print $output <<END;
			<form>$token</form>
			<lemma>$lemma</lemma>
			<tag>$tag</tag>
		</m>
END
}

1;