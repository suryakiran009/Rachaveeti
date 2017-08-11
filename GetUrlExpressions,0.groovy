import COM.FutureTense.Interfaces.ICS
import COM.FutureTense.Interfaces.FTValList

import java.text.Normalizer;
import java.text.SimpleDateFormat
import java.util.ArrayList;
import java.util.Collections
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils
import org.slf4j.Logger;
import org.slf4j.LoggerFactory

import com.fatwire.assetapi.data.BlobObject
import com.openmarket.xcelerate.asset.WebReferencesManager
import com.openmarket.xcelerate.site.SitePlanNode;
import com.fatwire.cs.core.db.PreparedStmt;
import com.fatwire.cs.core.db.StatementParam;
import com.fatwire.cs.core.webreference.WebReferencesBean
import com.fatwire.gst.foundation.facade.assetapi.AssetIdUtils;
import com.fatwire.gst.foundation.facade.runtag.siteplan.NodePath;
import com.fatwire.assetapi.data.AssetId
import com.openmarket.xcelerate.site.SitePlanNode
import com.openmarket.xcelerate.common.DimensionSupportManagerFactory
import com.fatwire.mda.Dimension;
import com.fatwire.mda.DimensionableAssetManager
import com.fatwire.services.util.AssetUtil;

import COM.FutureTense.Interfaces.IList
import com.openmarket.xcelerate.asset.AssetIdImpl
import com.openmarket.xcelerate.asset.SitePlanAsset;
import uk.co.manifesto.avios.wcs.util.DimensionUtil;

import java.io.File

// Utility class for Functions
public class FunctionUtil {
  private ICS ics;
  private static final Logger LOGGER = LoggerFactory.getLogger("customelements.openmarket.xcelerate.util.geturlexpressions");

  public FunctionUtil (ICS ics) {
    this.ics = ics;
  }

  // Gets the asset url of the given parent asset identified by ${_parentAssetId}
  public String getAssetURL (AssetId assetId) {
    String assetURL = null;
    WebReferencesManager webReferencesManager = new WebReferencesManager(ics);
    List<WebReferencesBean> webRefBeanList = webReferencesManager.getUrls(assetId);
    if (webRefBeanList != null && webRefBeanList.size() > 0)
    {
      for (WebReferencesBean webReferencesBean : webRefBeanList)
      {
        assetURL = webReferencesBean.getAssetURL();
      }
    }
    return assetURL;
  }

  // Returns true if the given asset 'assetId' has locale set. Otherwise, returns false.
  public boolean hasLocale (final AssetId assetId) {
    final String assetDimTable = assetId.getType() + "_Dim";

    PreparedStmt assetDimStatment = new PreparedStmt("SELECT * FROM " + assetDimTable + " WHERE cs_ownerid = ?", Collections.singletonList(assetDimTable));
    assetDimStatment.setElement(0, assetDimTable, "cs_ownerid");
    StatementParam param = assetDimStatment.newParam();
    param.setLong(0, assetId.getId());

    final IList assetDimList = ics.SQL(assetDimStatment, param, true);
    if (assetDimList != null && assetDimList.hasData()) {
      return true;
    }
    return false;
  }

  // Returns parent AssetId of the given 'assetId' per Site Plan Tree hierarchy.
  public AssetId getParentPage (final AssetId assetId) {
    final String sitePlanTreeTable = "SitePlanTree";
    PreparedStmt parentPageStatment = new PreparedStmt("SELECT otype, oid FROM " + sitePlanTreeTable + " WHERE nid = (SELECT nparentid FROM " + sitePlanTreeTable + " WHERE oid = ?)", Collections.singletonList(sitePlanTreeTable));
    parentPageStatment.setElement(0, sitePlanTreeTable, "oid");
    StatementParam param = parentPageStatment.newParam();
    param.setLong(0, assetId.getId());

    final IList parentPageList = ics.SQL(parentPageStatment, param, true);
    if (parentPageList != null && parentPageList.hasData()) {
      parentPageList.moveToRow(3, 1);
      try {
        return AssetIdUtils.createAssetId(parentPageList.getValue("otype"), parentPageList.getValue("oid"));
      } catch (NoSuchFieldException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    return null;
  }
}

public static class Functions
{
    public ICS ics;
    public static final String PAGE_ASSET_SUB_TYPE_HOME = "Home";
    public static final String PAGE_ASSET_SUB_TYPE_PAGE_FOLDER = "PageFolder";

    public Functions(ICS ics)
    {
        this.ics = ics;
    }

    public String spaceToUnderscore(String input)
    {
        return input.replaceAll("\\s", "_");
    }

    public String spaceToDash(String input)
    {
        return input.replaceAll("\\s", "-");
    }

    public String formatDate(Date input, String format)
    {
        SimpleDateFormat df = new SimpleDateFormat();
        df.applyPattern(format);
        return df.format(input);
    }

    public String listAsPath(List items, int max)
    {
        StringBuilder buf = new StringBuilder();
        int maxItems = max > items.size() ? items.size() : max;

        for(int x = 0; x < maxItems; x++)
        {
            Object item = items.get(x);
            buf.append(item.toString());
            if(x+1 < maxItems)
                buf.append("/");
        }
        return buf.toString();
    }

    public String getFileName(BlobObject blob)
    {
       String fileName = blob.getFilename();
        if(StringUtils.isNotEmpty(fileName))
        {
          // Remove the folder names from the file name"
          int lastIndex = fileName.lastIndexOf(File.separator);
          if(lastIndex> 0)
          {
            fileName = fileName.substring(lastIndex+1);
          }
          // Remove ,01" from the filenames
          // filename,10.jpg --> filename.jpg
          fileName = fileName.replaceAll("(,\\d*)\\.", ".");
        }
          return fileName;
    }

    public String property(String key, String defaultValue, String files)
    {
      String retVal;
      if(null != files  && files.length() > 0)
      {
        files = files.replaceAll(",", ";");
        retVal = COM.FutureTense.CS.Factory.newCS().GetProperty(key, files,true);
      }
      else
      {
        retVal = COM.FutureTense.CS.Factory.newCS().GetProperty(key);
      }
      return (null == retVal || retVal.trim().length() == 0) ? defaultValue : retVal.trim();
    }

    // Returns locale as path {country_code}/{language_code}
    public String localeAsPath(String locale)
    {
      return DimensionUtil.getLocaleAsPath(locale);
    }

    // Returns Parent Site Tree as Path
    public String parentSiteTreeAsPath(AssetId parentAssetId)
    {
      String assetURL = null;

      // Ignore if the parent Page asset is not of type 'Page' (e.g. SitePlan)
      if (parentAssetId != null && !SitePlanAsset.STANDARD_TYPE_NAME.equals(parentAssetId.getType()))
      {
        ICS _ics = COM.FutureTense.CS.Factory.newCS();
        String parentAssetSubType = AssetUtil.getSubtype(_ics, parentAssetId);
        // Ignores if the parent Page asset sub type is 'Home'
        if (!PAGE_ASSET_SUB_TYPE_HOME.equals(parentAssetSubType)) {
          FunctionUtil functionUtil = new FunctionUtil(_ics);
          AssetId finalAncestorPageAssetId = null;

          // If the parent is of sub type 'PageFolder', then gets it's parent (i.e. grand parent of the current Page asset) asset url
          // if it is not a SitePlan asset && not of sub type 'Home' && not of sub type 'PageFolder' again.
          if (PAGE_ASSET_SUB_TYPE_PAGE_FOLDER.equals(parentAssetSubType)) {
            AssetId grandParentAssetId = getParentPage(parentAssetId.getId());
            if (!SitePlanAsset.STANDARD_TYPE_NAME.equals(grandParentAssetId.getType())) {
              String grandParentAssetSubType = AssetUtil.getSubtype(_ics, grandParentAssetId);
              if (!PAGE_ASSET_SUB_TYPE_PAGE_FOLDER.equals(grandParentAssetSubType) && !PAGE_ASSET_SUB_TYPE_HOME.equals(grandParentAssetSubType)) {
                finalAncestorPageAssetId = grandParentAssetId;
              }
            }
          } else {
            finalAncestorPageAssetId = parentAssetId;
          }

          if (finalAncestorPageAssetId != null) {
            assetURL = functionUtil.getAssetURL(finalAncestorPageAssetId);
          }

          if (StringUtils.isNotEmpty(assetURL)) {
            if (functionUtil.hasLocale(finalAncestorPageAssetId)) {
              // Removes locale from the asset url
              assetURL = assetURL.substring(assetURL.indexOf("/", 3));
            }
          }
        }
      }
      return assetURL;
    }

    // Builds Page Url based on locale, URLPath attribute value and parent Site Tree structure/path
    public String buildPageUrlBySiteTree (String locale, AssetId parentAssetId, String urlPath) {
      StringBuilder pageUrlSB = new StringBuilder();
      if (StringUtils.isNotEmpty(urlPath)) {
        if (StringUtils.isNotEmpty(locale)) {
          // Adds locale (in  {country_code}/{language_code} format) to the Page Url
          pageUrlSB.append(DimensionUtil.getLocaleAsPath(locale));
        }

        // Appends parent Page Url (which would essentially be constructed based on the Site Tree structure again) to the Page Url
        String parentSiteTreeAsPath = parentSiteTreeAsPath(parentAssetId);
        if (StringUtils.isNotEmpty(parentSiteTreeAsPath)) {
          pageUrlSB.append(parentSiteTreeAsPath)
        }
        // Finally, appends current Page asset URLPath to the constructed Url.
        // Additionally, removes (special) characters that doesn't falls under the regex range [^A-Za-z0-9.-] from urlPath.
        pageUrlSB.append(pageUrlSB.length() > 0 ? "/" : "").append(spaceToDash(urlPath.trim()).replaceAll("[^A-Za-z0-9.-]",""));
      }
      return pageUrlSB.toString();
    }

    // Normalises the accent characters on the given string.
    public String normaliseAccents(String str) {
      String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
      Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
      return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    // Returns an empty string if given is NULL.
    public String defaultString(String str) {
      StringUtils.defaultString(str);
    }
}

// Use the Functions defined above to be used for expressions.
Map functionsMap = new HashMap();
functionsMap.put("f", new Functions(ics));
ics.SetObj("f", functionsMap);