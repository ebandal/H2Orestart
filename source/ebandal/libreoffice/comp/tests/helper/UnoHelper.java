package ebandal.libreoffice.comp.tests.helper;

import com.sun.star.beans.PropertyValue;
import com.sun.star.frame.FrameSearchFlag;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.text.XTextDocument;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import ebandal.libreoffice.comp.tests.base.UnoSuite;

public class UnoHelper {
	
	public static XTextDocument getWriterDocument() throws Exception {
		XMultiComponentFactory xMngr = UnoSuite.getComponentContext().getServiceManager();
        Object oDesktop = xMngr.createInstanceWithContext("com.sun.star.frame.Desktop", UnoSuite.getComponentContext());
        XComponentLoader xLoader = (XComponentLoader)UnoRuntime.queryInterface(
                XComponentLoader.class, oDesktop);

        XComponent xDoc = xLoader.loadComponentFromURL("private:factory/swriter", "_default",
                FrameSearchFlag.ALL, new PropertyValue[0]);

        return (XTextDocument)UnoRuntime.queryInterface(XTextDocument.class, xDoc);
	}

}
