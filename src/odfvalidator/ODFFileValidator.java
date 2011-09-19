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

import java.io.File;

import org.openoffice.odf.pkg.OdfPackage;

/**
 * Validator for Files
 */
public class ODFFileValidator extends ODFRootPackageValidator {
    
    private File m_aFile = null;

        /** Creates a new instance of ODFFileValidator */
    public ODFFileValidator( File aFile, int nLogLevel, 
                             int nMode,
                             SAXParseExceptionFilter aFilter,
                             ODFValidatorProvider aValidatorProvider ) throws ODFValidatorException
    {
        super( nLogLevel, nMode, aFilter, aValidatorProvider );
        m_aFile = aFile;
    }

    protected String getLoggerName()
    {
        return m_aFile.getAbsolutePath();
    }
    
    protected OdfPackage getPackage() throws Exception
    {
        return OdfPackage.loadPackage( m_aFile );
    }

}
