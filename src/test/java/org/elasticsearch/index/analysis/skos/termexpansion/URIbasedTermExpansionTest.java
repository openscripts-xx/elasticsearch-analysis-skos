/**
 * Copyright 2010 Bernhard Haslhofer
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.elasticsearch.index.analysis.skos.termexpansion;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.elasticsearch.index.analysis.skos.SKOSAnalyzer;
import org.elasticsearch.index.analysis.skos.SKOSAnalyzer.ExpansionType;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * This test-case verifies and demonstrates the "Expansion of URI terms to SKOS
 * labels" use case as described in https://code.
 * google.com/p/lucene-skos/wiki/UseCases#UC1:_URI-based_term_expansion
 */
public class URIbasedTermExpansionTest extends AbstractTermExpansionTest {

    /**
     * This test indexes a sample metadata record (=lucene document) having a
     * "title", "description", and "subject" field, which is semantically
     * enriched by a URI pointing to a SKOS concept "weapons".
     *
     * A search for "arms" returns that record as a result because "arms" is
     * defined as an alternative label (altLabel) for the concept "weapons".
     *
     * @throws IOException
     */
    @Test
    public void uriBasedTermExpansion() throws IOException {

        /* defining the document to be indexed */
        Document doc = new Document();
        doc.add(new Field("title", "Spearhead", Field.Store.YES,
                Field.Index.ANALYZED));
        doc.add(new Field(
                "description",
                "Roman iron spearhead. The spearhead was attached to one end of a wooden shaft..."
                + "The spear was mainly a thrusting weapon, but could also be thrown. "
                + "It was the principal weapon of the auxiliary soldier... "
                + "(second - fourth century, Arbeia Roman Fort).", Field.Store.NO,
                Field.Index.ANALYZED));
        doc.add(new Field("subject",
                "http://www.ukat.org.uk/thesaurus/concept/859", Field.Store.NO,
                Field.Index.ANALYZED));

        /* setting up the SKOS analyzer */
        String skosFile = "src/test/resources/skos_samples/ukat_examples.n3";
        String indexPath = "target";

        /* ExpansionType.URI->the field to be analyzed (expanded) contains URIs */
        Analyzer skosAnalyzer = new SKOSAnalyzer(matchVersion, indexPath, skosFile,
                ExpansionType.URI);

        /* Define different analyzers for different fields */
        Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
        analyzerPerField.put("subject", skosAnalyzer);
        PerFieldAnalyzerWrapper indexAnalyzer = new PerFieldAnalyzerWrapper(
                new SimpleAnalyzer(matchVersion), analyzerPerField);

        /* setting up a writer with a default (simple) analyzer */
        writer = new IndexWriter(new RAMDirectory(), new IndexWriterConfig(
                matchVersion, indexAnalyzer));

        /* adding the document to the index */
        writer.addDocument(doc);

        /* defining a query that searches over all fields */
        BooleanQuery query1 = new BooleanQuery();
        query1.add(new TermQuery(new Term("title", "arms")),
                BooleanClause.Occur.SHOULD);
        query1.add(new TermQuery(new Term("description", "arms")),
                BooleanClause.Occur.SHOULD);
        query1.add(new TermQuery(new Term("subject", "arms")),
                BooleanClause.Occur.SHOULD);

        /* creating a new searcher */
        searcher = new IndexSearcher(IndexReader.open(writer, false));

        TopDocs results = searcher.search(query1, 10);

        /* the document matches because "arms" is among the expanded terms */
        Assert.assertEquals(1, results.totalHits);

        /* defining a query that searches for a broader concept */
        Query query2 = new TermQuery(new Term("subject", "military equipment"));

        results = searcher.search(query2, 10);

        /* ... also returns the document as result */
        Assert.assertEquals(1, results.totalHits);

    }
}
