#!C:\strawberry\perl\bin\perl -w
package LvTreeBank::Utils::NormalizeIdsBatch;

use LvTreeBank::Utils::NormalizeIds;

use IO::File;
use IO::Dir;

###############################################################################
# Batch processing for LvTreeBank::Utils::NormalizeIds - if single argument
# provided, treat it as directory and process all files in it. Otherwise pass
# all arguments to NormalizeIds.
#
# Developed on Strawberry Perl 5.12.3.0
# Latvian Treebank project, 2012
# Lauma Pretkalnina, LUMII, AILab, lauma@ailab.lv
# Licenced under GPL.
###############################################################################
sub normalizeIdsBatch
{
	if (@ARGV eq 1)
	{

		my $dir_name = $ARGV[0];
		my $dir = IO::Dir->new($dir_name) or die "dir $!";

		while (defined(my $in_file = $dir->read))
		{
			if ((! -d "$dir_name/$in_file") and ($in_file =~ /^(.+)\.w$/))
			{
				LvTreeBank::Utils::NormalizeIds::normalizeIds ($dir_name, $1, $1);
			}
		}

	}
	else
	{
		LvTreeBank::Utils::NormalizeIds::normalizeIds (@ARGV);
	}
}
1;