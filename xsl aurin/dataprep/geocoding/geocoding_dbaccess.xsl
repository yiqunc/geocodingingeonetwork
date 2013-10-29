<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	version="1.0">
	<xsl:output method="html" encoding="utf-8" indent="yes"
		doctype-public="-//W3C//DTD HTML 4.01//EN" doctype-system="http://www.w3.org/TR/html4/strict.dtd" />

	<xsl:include href="../../../../home/dataprep/geocoding/main.xsl" />

	<xsl:template match="/">
		<xsl:call-template name="geocoding_dbaccess" >
				 
		</xsl:call-template>
	</xsl:template>

</xsl:stylesheet>
