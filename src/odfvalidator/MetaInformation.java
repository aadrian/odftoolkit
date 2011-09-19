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

package odfvalidator;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;

import org.openoffice.odf.pkg.OdfPackage;

public class MetaInformation {

    private PrintStream m_aOut;
    
    /** Creates a new instance of Validator */
    public MetaInformation( PrintStream aOut ) {
        
        m_aOut = aOut;
    }

    public void getInformation( String aDocFileName ) throws ODFValidatorException
    {
        try
        {
            OdfPackage aDocFile = OdfPackage.loadPackage( aDocFileName );
            
            getGenerator( aDocFile );
        }
        catch( Exception e )
        {
            throw new ODFValidatorException( aDocFileName, "", e );
        }  
    }
    
    public void getGenerator( OdfPackage aDocFile ) throws ODFValidatorException
    {
        try
        {
            InputStream aInStream = aDocFile.getInputStream(OdfPackage.OdfFile.MANIFEST.getPath());
            Logger aLogger = new Logger(aDocFile.getBaseURI(),OdfPackage.OdfFile.MANIFEST.getPath(),m_aOut, Logger.INFO);
        
            getInformation( aInStream, aLogger );
        }
        catch( Exception e )
        {
            throw new ODFValidatorException( aDocFile.getBaseURI(), "", e );
        }  
    }


    
    private void getInformation( InputStream aInStream, Logger aLogger ) throws IOException, ODFValidatorException
    {
        SAXParser aParser = null;
        try
        {
            SAXParserFactory aParserFactory = SAXParserFactory.newInstance();
            aParserFactory.setNamespaceAware(true);
            aParser = aParserFactory.newSAXParser();

            XMLFilter aFilter =  new MetaFilter(aLogger,null);
            aFilter.setParent( aParser.getXMLReader() ) ;

            aFilter.parse(new InputSource(aInStream));
        }
        catch( javax.xml.parsers.ParserConfigurationException e )
        {
            throw new ODFValidatorException( e );
        }
        catch( org.xml.sax.SAXException e )
        {
            throw new ODFValidatorException( e );
        }
    }

}
