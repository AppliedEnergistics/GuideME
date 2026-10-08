import MiniSearch, { type SearchResult } from "minisearch";

/**
 * An entry of the search index written by the website generator.
 */
type SearchEntry = {
  /**
   * The path of the page relative to the root of the website.
   */
  url: string;
  title: string;
  text: string;
};

export type SearchHit = {
  url: string;
  title: string;
  /**
   * An excerpt of the page text around the first match.
   */
  snippet: string;
  /**
   * The terms of the page that matched the query.
   */
  terms: string[];
};

// Languages such as Japanese don't separate words with spaces, which the segmenter handles
const segmenter =
  typeof Intl !== "undefined" && "Segmenter" in Intl
    ? new Intl.Segmenter(undefined, { granularity: "word" })
    : null;

function tokenize(text: string): string[] {
  if (segmenter) {
    const tokens: string[] = [];
    for (const segment of segmenter.segment(text)) {
      if (segment.isWordLike) {
        tokens.push(segment.segment);
      }
    }
    return tokens;
  }
  return text.split(/[\s\p{P}]+/u).filter((token) => token.length > 0);
}

const SNIPPET_LENGTH = 120;

function createSnippet(text: string, terms: string[]): string {
  const lowerText = text.toLowerCase();
  let matchIndex = -1;
  for (const term of terms) {
    const index = lowerText.indexOf(term.toLowerCase());
    if (index !== -1 && (matchIndex === -1 || index < matchIndex)) {
      matchIndex = index;
    }
  }

  const start = Math.max(0, matchIndex - SNIPPET_LENGTH / 3);
  const end = Math.min(text.length, start + SNIPPET_LENGTH);
  return (
    (start > 0 ? "…" : "") +
    text.substring(start, end) +
    (end < text.length ? "…" : "")
  );
}

export class GuideSearch {
  private readonly miniSearch: MiniSearch<SearchEntry>;

  private constructor(entries: SearchEntry[]) {
    this.miniSearch = new MiniSearch<SearchEntry>({
      idField: "url",
      fields: ["title", "text"],
      storeFields: ["url", "title", "text"],
      tokenize,
      searchOptions: {
        boost: { title: 3 },
        prefix: true,
        fuzzy: 0.2,
        combineWith: "AND",
      },
    });
    this.miniSearch.addAll(entries);
  }

  static async load(indexUrl: string): Promise<GuideSearch> {
    const response = await fetch(indexUrl);
    if (!response.ok) {
      throw new Error(
        `Failed to load search index ${indexUrl}: HTTP Status ${response.status}`,
      );
    }
    return new GuideSearch((await response.json()) as SearchEntry[]);
  }

  search(query: string, limit: number): SearchHit[] {
    return this.miniSearch
      .search(query)
      .slice(0, limit)
      .map((result: SearchResult) => ({
        url: result.url as string,
        title: result.title as string,
        snippet: createSnippet(result.text as string, result.terms),
        terms: result.terms,
      }));
  }
}
