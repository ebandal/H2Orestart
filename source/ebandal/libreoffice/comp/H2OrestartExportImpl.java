/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */
package ebandal.libreoffice.comp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.star.beans.PropertyValue;
import com.sun.star.io.XOutputStream;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XSingleComponentFactory;
import com.sun.star.lib.uno.helper.Factory;
import com.sun.star.lib.uno.helper.WeakBase;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.uno.AnyConverter;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

import HwpxExport.LoToHwpx;
import HwpxWriter.HwpxPackage;
import HwpxWriter.WDocument;

/**
 * HWPX 내보내기 필터 (Writer 문서 → HWPX).
 * 가져오기(H2OrestartImpl) 와 분리된 컴포넌트. 필터 Hwpx_Writer 가 이 서비스를 쓴다.
 */
public final class H2OrestartExportImpl extends WeakBase implements com.sun.star.lang.XInitialization,
                                                                   com.sun.star.document.XExporter,
                                                                   com.sun.star.document.XFilter {
    private static final Logger log = Logger.getLogger(H2OrestartExportImpl.class.getName());

    private static final String m_implementationName = H2OrestartExportImpl.class.getName();
    public static final String __serviceName = "ebandal.libreoffice.H2OrestartExport";
    private static final String[] m_serviceNames = { __serviceName };

    private XComponent sourceDocument;
    private final XComponentContext context;

    public H2OrestartExportImpl(XComponentContext context) {
        this.context = context;
    }

    public static XSingleComponentFactory __getComponentFactory(String sImplementationName) {
        if (sImplementationName.equals(m_implementationName)) {
            return Factory.createComponentFactory(H2OrestartExportImpl.class, m_serviceNames);
        }
        return null;
    }

    public static boolean __writeRegistryServiceInfo(XRegistryKey xRegistryKey) {
        return Factory.writeRegistryServiceInfo(m_implementationName, m_serviceNames, xRegistryKey);
    }

    @Override
    public void initialize(Object[] args) {
    }

    @Override
    public void setSourceDocument(XComponent document) throws IllegalArgumentException {
        this.sourceDocument = document;
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean filter(PropertyValue[] descriptor) {
        XOutputStream xout = null;
        String url = null;
        for (PropertyValue pv : descriptor) {
            if ("OutputStream".equals(pv.Name)) {
                xout = UnoRuntime.queryInterface(XOutputStream.class, pv.Value);
            } else if ("URL".equals(pv.Name)) {
                try {
                    url = AnyConverter.toString(pv.Value);
                } catch (com.sun.star.lang.IllegalArgumentException e) {
                    url = null;
                }
            }
        }
        try {
            WDocument doc = LoToHwpx.convert(sourceDocument, context);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            HwpxPackage.write(doc, bos);
            byte[] data = bos.toByteArray();
            if (xout != null) {
                xout.writeBytes(data);
                xout.flush();
            } else if (url != null && url.startsWith("file:")) {
                OutputStream os = new FileOutputStream(new File(new URI(url)));
                try {
                    os.write(data);
                } finally {
                    os.close();
                }
            } else {
                log.severe("HWPX export: no OutputStream or file URL");
                return false;
            }
            log.info("HWPX export: " + data.length + " bytes, paragraphs="
                    + (doc.sections.isEmpty() ? 0 : doc.sections.get(0).paragraphs.size()));
            return true;
        } catch (Throwable e) {
            log.log(Level.SEVERE, "HWPX export failed: " + e.toString(), e);
            return false;
        }
    }
}
