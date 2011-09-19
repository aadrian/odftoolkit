/************************************************************************
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * Use is subject to license terms.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. You can also
 * obtain a copy of the License at http://odftoolkit.org/docs/license.txt
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ************************************************************************/

package org.odftoolkit.odfvalidator;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.ZipException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Validator;
import org.odftoolkit.odfdom.doc.OdfDocument;
import org.odftoolkit.odfdom.pkg.OdfPackage;
import org.odftoolkit.odfdom.pkg.manifest.EncryptionData;
import org.odftoolkit.odfdom.pkg.manifest.OdfFileEntry;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;

import org.xml.sax.helpers.DefaultHandler;

/**
 * Validator for Files
 */
public abstract class ODFPackageValidator {

    static final String DOCUMENT_SETTINGS = "document-settings";
    static final String DOCUMENT_STYLES = "document-styles";
    static final String DOCUMENT_CONTENT = "document-content";

    protected Logger.LogLevel m_nLogLevel;
    protected OdfValidatorMode m_eMode = OdfValidatorMode.CHECK_CONFORMANCE;
    protected SAXParseExceptionFilter m_aFilter = null;
    protected ODFValidatorProvider m_aValidatorProvider = null;

    protected ODFValidationResult m_aResult = null;
    protected OdfVersion m_aConfigVersion = null;

    private SAXParserFactory m_aSAXParserFactory = null;


    ODFPackageValidator( Logger.LogLevel nLogLevel, OdfValidatorMode eMode, OdfVersion aVersion,
                             SAXParseExceptionFilter aFilter,ODFValidatorProvider aValidatorProvider) {
        m_nLogLevel = nLogLevel;
        m_eMode = eMode;
        m_aFilter = aFilter;
        m_aValidatorProvider = aValidatorProvider;
        m_aConfigVersion = aVersion;
        m_aResult = new ODFValidationResult( aVersion, eMode );
    }
    
       

    abstract String getLoggerName();
    
    abstract OdfPackage getPackage( Logger aLogger );
    
    abstract String getStreamName( String aEntry );
    
    public boolean validate(PrintStream aOut) throws ODFValidatorException
    {
        Logger aLogger = new Logger( getLoggerName(), "", aOut, m_nLogLevel);

        boolean bHasErrors = false;

        OdfPackage aPkg = getPackage( aLogger );
        if( aPkg == null )
            return true;

        try
        {
            String aDocVersion = getVersion( aLogger );
            if( aDocVersion != null )
                aLogger.logInfo( "ODF Version: " + aDocVersion, false );
            OdfVersion aVersion = m_aConfigVersion == null ? OdfVersion.valueOf(aDocVersion,true) : m_aConfigVersion;

            bHasErrors |= validatePre(aOut, aVersion);
            aLogger.logInfo( "Media Type: " + m_aResult.getMediaType(), false);

            bHasErrors |= validateMeta(aOut, getStreamName( OdfDocument.OdfXMLFile.META.getFileName()), aVersion, true );
            bHasErrors |= validateEntry(aOut, getStreamName(OdfDocument.OdfXMLFile.SETTINGS.getFileName()), DOCUMENT_SETTINGS, aVersion);
            bHasErrors |= validateEntry(aOut, getStreamName( OdfDocument.OdfXMLFile.STYLES.getFileName()), DOCUMENT_STYLES, aVersion );
            if( m_aResult.getMediaType().equals(ODFMediaTypes.FORMULA_MEDIA_TYPE))
                bHasErrors |= validateMathML(aOut, getStreamName( OdfDocument.OdfXMLFile.CONTENT.getFileName()), aVersion );
            else
                bHasErrors |= validateEntry(aOut, getStreamName( OdfDocument.OdfXMLFile.CONTENT.getFileName()), DOCUMENT_CONTENT, aVersion );
            bHasErrors |= validatePost(aOut, aLogger, aVersion);
        }
        catch( ZipException e )
        {
            aLogger.logFatalError( e.getMessage() );
        }
        catch( IOException e )
        {
            aLogger.logFatalError( e.getMessage() );
        }

        logSummary( bHasErrors, aLogger );
            
        return bHasErrors || aLogger.hasError();
    }

    protected boolean validatePre(PrintStream aOut, OdfVersion aVersion ) throws ODFValidatorException, IOException
    {
        return false;
    }

    protected boolean validatePost(PrintStream aOut, Logger aLogger, OdfVersion aVersion ) throws ODFValidatorException, IOException
    {
        return false;
    }

    protected void logSummary( boolean bHasErrors, Logger aLogger )
    {
    }

    protected boolean validateEntry(PrintStream aOut, String aEntryName, String aLocalElementName, OdfVersion aVersion ) throws IOException, ZipException, IllegalStateException, ODFValidatorException
    {
        Logger aLogger = new Logger(getLoggerName(),aEntryName,aOut, m_nLogLevel);
        XMLFilter aFilter = new ContentFilter(aLogger, aLocalElementName );
        if( (m_eMode == OdfValidatorMode.CHECK_CONFORMANCE && aVersion.compareTo(OdfVersion.V1_1) <= 0) ||
            m_eMode == OdfValidatorMode.CHECK_EXTENDED_CONFORMANCE )
        {
            XMLFilter aAlienFilter = new ForeignContentFilter(aLogger,aVersion,m_aResult);
            aAlienFilter.setParent(aFilter);
            aFilter = aAlienFilter;
        }
        Validator aValidator = m_eMode == OdfValidatorMode.VALIDATE_STRICT ? m_aValidatorProvider.getStrictValidator(aOut, aVersion)
                                                          : m_aValidatorProvider.getValidator(aOut,aVersion);
        return validateEntry(aOut, aFilter, aValidator, aLogger, aEntryName );
    }

    private boolean validateMeta(PrintStream aOut, String aEntryName, OdfVersion aVersion, boolean bIsRoot) throws IOException, ZipException, IllegalStateException, ODFValidatorException
    {
        Logger aLogger = new Logger(getLoggerName(),aEntryName,aOut, m_nLogLevel);
        XMLFilter aFilter = new MetaFilter(aLogger, m_aResult );
        if( (m_eMode == OdfValidatorMode.CHECK_CONFORMANCE && aVersion.compareTo(OdfVersion.V1_1) <= 0) ||
            m_eMode == OdfValidatorMode.CHECK_EXTENDED_CONFORMANCE )
        {
            XMLFilter aAlienFilter = new ForeignContentFilter(aLogger,aVersion,m_aResult);
            aAlienFilter.setParent(aFilter);
            aFilter = aAlienFilter;
        }

        Validator aValidator = m_eMode == OdfValidatorMode.VALIDATE_STRICT ? m_aValidatorProvider.getStrictValidator(aOut,aVersion)
                                                          : m_aValidatorProvider.getValidator(aOut,aVersion);
        return validateEntry(aOut, aFilter, aValidator, aLogger, aEntryName );
    }

    private boolean validateMathML(PrintStream aOut, String aEntryName, OdfVersion aVersion ) throws IOException, ZipException, IllegalStateException, ODFValidatorException
    {
        Logger aLogger = new Logger(getLoggerName(),aEntryName,aOut, m_nLogLevel);
        String aMathMLDTDSystemId = m_aValidatorProvider.getMathMLDTDSystemId(aVersion);
        if( aMathMLDTDSystemId != null )
        {
            // validate using DTD
            return parseEntry(aOut, new MathML101Filter(aMathMLDTDSystemId, aLogger), aLogger, aEntryName, true);
        }
        else
        {
            Validator aMathMLValidator = m_aValidatorProvider.getMathMLValidator(aOut,null);
            if( aMathMLValidator == null )
            {
                aLogger.logInfo( "MathML schema is not available. Validation has been skipped.", false);
                return false;
            }
            return validateEntry( aOut, new MathML20Filter(aLogger), aMathMLValidator, aLogger, aEntryName );
        }
    }
    

    protected boolean validateDSig(PrintStream aOut, String aEntryName, OdfVersion aVersion ) throws IOException, ZipException, IllegalStateException, ODFValidatorException
    {
        Validator aValidator=m_aValidatorProvider.getDSigValidator(aOut,aVersion);
        Logger aLogger = new Logger(getLoggerName(),aEntryName,aOut, m_nLogLevel);
        if ( aValidator == null ) {
            aLogger.logWarning("Signature not validated because there is no Signature Validator configured for the selected Configuration");
            return false;
        }

        return validateEntry(aOut, new DSigFilter(aLogger), aValidator, aLogger, aEntryName );
    }

    protected boolean validateEntry(PrintStream aOut, XMLFilter aFilter,
                           Validator aValidator, Logger aLogger,
                           String aEntryName ) throws IOException, ZipException, IllegalStateException, ODFValidatorException
    {
        OdfPackage aPkg = getPackage(aLogger);
        
        if( !aEntryName.equals(OdfPackage.OdfFile.MANIFEST.getPath()) && isEncrypted(aEntryName,aLogger) )
            return false;
        
        InputStream aInStream = null;
        try
        {
            aInStream = aPkg.getInputStream(aEntryName);
        }
        catch( Exception e )
        {
            throw new ODFValidatorException( e );
        }
                

        if ( aValidator == null ) {
            
            aLogger.logWarning("no Validator configured in selected Configuration for this file type");
            return false;
        }



        return aInStream != null ? validate(aOut, aInStream, aFilter, aValidator, aLogger ) : false;
    }
    
    private boolean validate(PrintStream aOut, InputStream aInStream,
                      XMLFilter aFilter,
                      javax.xml.validation.Validator aValidator,
                      Logger aLogger ) throws ODFValidatorException
    {
        SAXParser aParser = getSAXParser(false);
        SchemaErrorHandler aErrorHandler = new SchemaErrorHandler(aLogger, m_aFilter );

        try
        {
            XMLReader aReader;
            if( aFilter != null )
            {
                XMLReader aParent = aFilter.getParent();
                if( aParent != null )
                    ((XMLFilter)aParent).setParent( aParser.getXMLReader() ) ;
                else
                    aFilter.setParent( aParser.getXMLReader() ) ;
                aReader = aFilter;
            }
            else
            {
                aReader = aParser.getXMLReader();
            }

            if( m_aFilter != null )
            {
                m_aFilter.startSubFile();
            }
            aValidator.setErrorHandler(aErrorHandler);
            try
            {
                aValidator.validate( new SAXSource(aReader,
                                       new InputSource( aInStream ) ));
            }
            catch( RuntimeException e )
            {
                aLogger.logFatalError(e.getMessage());
                m_aValidatorProvider.resetValidatorProvider();
            }
        }
        catch( org.xml.sax.SAXParseException e )
        {
            aErrorHandler.fatalErrorNoException(e);
        }
        catch( org.xml.sax.SAXException e )
        {
            aLogger.logFatalError(e.getMessage());
        }
        catch( IOException e )
        {
            aLogger.logFatalError(e.getMessage());
        }
        
        aLogger.logInfo( aLogger.hasError() ? "validation errors found" : "no errors" , false);
        if( m_aResult.hasForeignElements())
        {
            Set<String> aForeignElementURISet = m_aResult.getForeignElements().keySet();
            StringBuilder aBuffer = new StringBuilder();
            Iterator<String> aIter = aForeignElementURISet.iterator();
            boolean bFirst = true;
            while( aIter.hasNext() )
            {
                String aURI = aIter.next();
                aBuffer.setLength(0);
                aBuffer.append( m_aResult.getForeignElements().get(aURI) );
                aBuffer.append( " extension elements from the following namespace were found: " );
                aBuffer.append( aURI );
                aLogger.logInfo(aBuffer.toString(), false);
            }
        }
        if( m_aResult.hasForeignAttributes())
        {
            Set<String> aForeignAttributeURISet = m_aResult.getForeignAttributes().keySet();
            Iterator<String> aIter = aForeignAttributeURISet.iterator();
            StringBuilder aBuffer = new StringBuilder();
            while( aIter.hasNext() )
            {
                String aURI = aIter.next();
                aBuffer.setLength(0);
                aBuffer.append( m_aResult.getForeignAttributes().get(aURI) );
                aBuffer.append( " extension attributes from the following namespace were found: " );
                aBuffer.append( aURI );
                aLogger.logInfo(aBuffer.toString(), false);
            }
        }
        return aLogger.hasError();
    }

    protected boolean parseEntry(PrintStream aOut, XMLFilter aFilter,
                           Logger aLogger,
                           String aEntryName , boolean bValidating) throws IOException, ZipException, IllegalStateException, ODFValidatorException
    {
        OdfPackage aPkg = getPackage(aLogger);

        if( isEncrypted(aEntryName,aLogger) )
            return false;
        
        InputStream aInStream = null;
        try
        {
            aInStream = getPackage(aLogger).getInputStream(aEntryName);
        }
        catch( Exception e )
        {
            throw new ODFValidatorException( e );
        }

        return aInStream != null ? parse(aOut, aInStream, aFilter, bValidating, aLogger ) : false;
    }

    private boolean parse(PrintStream aOut, InputStream aInStream, XMLFilter aFilter, boolean bValidating, Logger aLogger ) throws ODFValidatorException
    {
        SAXParser aParser = getSAXParser(bValidating);
        aLogger.setOutputStream(aOut);
        SchemaErrorHandler aErrorHandler = new SchemaErrorHandler( aLogger, m_aFilter );

        try
        {
            XMLReader aReader;
            if( aFilter != null )
            {
                aFilter.setParent( aParser.getXMLReader() );
                aReader = aFilter;
            }
            else
            {
                aReader = aParser.getXMLReader();
            }
            if( m_aFilter != null )
            {
                m_aFilter.startSubFile();
            }
            aReader.setErrorHandler(aErrorHandler);
            aReader.parse(new InputSource(aInStream));
        }
        catch( org.xml.sax.SAXParseException e )
        {
            aErrorHandler.fatalErrorNoException(e);
        }
        catch( org.xml.sax.SAXException e )
        {
            aLogger.logFatalError(e.getMessage());
        }
        catch( IOException e )
        {
            aLogger.logFatalError(e.getMessage());
        }
        
        if( bValidating )
            aLogger.logInfo( aLogger.hasError() ? "validation errors found" : "no errors" , false);            
        return aLogger.hasError();
    }

    private boolean isEncrypted( String aEntryName, Logger aLogger )
    {
        OdfFileEntry aFileEntry = getPackage(aLogger).getFileEntry(aEntryName);
        if ( aFileEntry != null )
        {
            EncryptionData aEncData=aFileEntry.getEncryptionData();
            if ( aEncData != null ) {
                 aLogger.logFatalError( "stream content is encrypted. Validataion of encrypted content is not supported.");                
                 return true;
            }
        }
        return false;
    }
    
        
    private SAXParser getSAXParser(boolean bValidating) throws ODFValidatorException
    {
        SAXParser aParser = null;
        if( m_aSAXParserFactory == null )
        {
            m_aSAXParserFactory = SAXParserFactory.newInstance();
            m_aSAXParserFactory.setNamespaceAware(true);
        }

        try
        {
            m_aSAXParserFactory.setValidating(bValidating);
            aParser = m_aSAXParserFactory.newSAXParser();
        }
        catch( javax.xml.parsers.ParserConfigurationException e )
        {
            throw new ODFValidatorException( e );
        }
        catch( org.xml.sax.SAXException e )
        {
            throw new ODFValidatorException( e );
        }
        
        return aParser;
    }

    /**
     * get the generator
     */
    public String getGenerator() {
        return m_aResult.getGenerator();
    }

    private String getVersion(Logger aLogger) throws ODFValidatorException
    {
        String aVersion = null;

        InputStream aInStream = null;
        try
        {
            OdfPackage aPkg = getPackage(aLogger);
            aInStream = aPkg.getInputStream(getStreamName(OdfDocument.OdfXMLFile.META.getFileName()));
            if( aInStream == null )
                aInStream = aPkg.getInputStream(getStreamName(OdfDocument.OdfXMLFile.SETTINGS.getFileName()));
            if( aInStream == null )
                aInStream = aPkg.getInputStream(getStreamName(OdfDocument.OdfXMLFile.CONTENT.getFileName()));
        }
        catch( Exception e )
        {
            aLogger.logFatalError(e.getMessage());
        }
        
        SAXParser aParser = getSAXParser(false);
        
        DefaultHandler aHandler = new VersionHandler();
        
        try
        {
            aParser.parse(aInStream, aHandler);
        }
        catch( SAXVersionException e )
        {
            aVersion = e.getVersion();
        }
        catch( org.xml.sax.SAXException e )
        {
            aLogger.logFatalError(e.getMessage());
        }
        catch( IOException e )
        {
            aLogger.logFatalError(e.getMessage());
        }
 
        return aVersion;
    }

}