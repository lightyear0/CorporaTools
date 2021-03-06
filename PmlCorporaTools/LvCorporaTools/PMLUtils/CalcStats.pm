#!C:\strawberry\perl\bin\perl -w
package LvCorporaTools::PMLUtils::CalcStats;

use strict;
use warnings;
#use utf8;

use Exporter();
our @ISA = qw(Exporter);
our @EXPORT_OK = qw(calcStats processDir);

use Data::Dumper;
use IO::File;
use IO::Dir;
use LvCorporaTools::GenericUtils::SimpleXmlIo qw(loadXml @FORCE_ARRAY_M);

###############################################################################
# Calculates sentence length statistics from LV-PML .m file.
#
# Input files - utf8.
# Output file - list with sentence counts ordered by length.
#
# Developed on Strawberry Perl 5.12.3.0
# Latvian Treebank project, 2011-2013
# Lauma Pretkalnina, LUMII, AILab, lauma@ailab.lv
# Licenced under GPL.
###############################################################################

# Calculate statistics in single file. This can be used as entry point, if this
# module is used standalone.
sub calcStats
{
	autoflush STDOUT 1;
	if (not @_ or @_ < 1)
	{
		print <<END;
Script for calculating sentence length statistics from given LV-PML .m file.

Params:
   directory prefix
   file name
   output file name [opt, "file_name-stats.txt" used otherwise]

Latvian Treebank project, LUMII, 2013, provided under GPL
END
		exit 1;
	}
	# Input paramaters.
	my $dirPrefix = shift @_;
	my $inputName = shift @_;
	my $resName = (shift @_ or "$inputName-stats.txt");
	
	my $mapping = &_processM($dirPrefix, $inputName);
	&_doOutput($dirPrefix, $resName, $mapping);
	print "CalcStats has finished procesing \"$inputName\".\n";
}

# Calculate statistics in all files in given folder. This can be used as entry
# point, if this module is used standalone.
sub processDir
{
	autoflush STDOUT 1;
	if (not @_ or @_ < 1)
	{
		print <<END;
Script for calculating sentence length statistics from LV-PML .m files in
given data directory.

Params:
   data directory 

Latvian Treebank project, LUMII, 2013, provided under GPL
END
		exit 1;
	}
	my $dirPrefix = $_[0];
	my $dir = IO::Dir->new($dirPrefix) or die "dir $!";
	my %mapping = ();

	while (defined(my $inputName = $dir->read))
	{
		if ((! -d "$dirPrefix/$inputName") and ($inputName =~ /^(.+)\.m$/))
		{
			#checkLvPml ($dir_name, $1, "$1-errors.txt");
			%mapping = %{&_processM($dirPrefix, $inputName, \%mapping)};
			print "calcStatsBatch has finished procesing \"$inputName\".\n";
		}
	}
	&_doOutput($dirPrefix, "stats.txt", \%mapping);
}


# _processM(directory prefix, input file name, hash with previous statistics
#			[opt])
# Returns hash with updated statistics.
# Collects statistics from single .m file and puts them into hash tha can
# (optionally) contain statistics from other files.
sub _processM
{
	my $dirPrefix = shift @_;
	my $inputName = shift @_;
	my $mapping = shift @_;
	my %res = $mapping ? %$mapping : ();

	my $m = loadXml("$dirPrefix\\$inputName", \@FORCE_ARRAY_M);
	my @sizes = map {scalar @{$_->{'m'}}} @{$m->{'xml'}->{'s'}};
	for (@sizes)
	{
		$res{$_}++;
	}
	return \%res;
}

# _doOutput(directory prefix, output file name, hash with statistics)
# Prints the statistics in the output file.
sub _doOutput
{
	my $dirPrefix = shift @_;
	my $resName = shift @_;
	my $mapping = shift @_;
	
	my $out = IO::File->new("$dirPrefix\\$resName", "> :encoding(UTF-8)")
		or die "Could not create file $resName: $!";
	print $out "Length\tCount\n";
	for (sort {$a <=> $b} keys(%$mapping))
	{
		print $out "$_\t$mapping->{$_}\n";
	}
	$out->close;
}

1;