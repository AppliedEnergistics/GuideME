package guideme.web;

/**
 * Determines where pages are written to and how they are linked to. All paths are relative to the root of the website
 * and have no leading slash.
 *
 * @param cleanUrls If true, pages are written as {@code page/index.html} and linked to as {@code page/}, which allows
 *                  linking to pages without a file extension on any static web host.
 */
record SitePaths(ExportedGuideImpl guide, boolean cleanUrls) {
    /**
     * {@return the path of the HTML file a page is written to}
     */
    String pageFile(String pageId) {
        var basePath = guide.getPageBasePath(pageId);
        return cleanUrls ? basePath + "/index.html" : basePath + ".html";
    }

    /**
     * {@return the path used to link to a page}
     */
    String pageUrl(String pageId) {
        var basePath = guide.getPageBasePath(pageId);
        return cleanUrls ? basePath + "/" : basePath + ".html";
    }

    /**
     * {@return the relative URL from a file to the root of the website, i.e. "../" for files in a sub-folder}
     */
    static String relativePathToRoot(String file) {
        var depth = (int) file.chars().filter(c -> c == '/').count();
        return "../".repeat(depth);
    }
}
