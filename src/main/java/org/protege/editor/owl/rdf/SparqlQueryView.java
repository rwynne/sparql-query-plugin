package org.protege.editor.owl.rdf;

import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.protege.editor.core.ui.error.ErrorLogPanel;
import org.protege.editor.owl.rdf.repository.BasicSparqlReasonerFactory;
import org.protege.editor.owl.ui.renderer.OWLCellRenderer;
import org.protege.editor.owl.ui.table.BasicOWLTable;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;
import org.protege.owl.rdf.Utilities;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SparqlQueryView extends AbstractOWLViewComponent {
	private static final long serialVersionUID = -1370725700740073290L;
	
	private SparqlReasoner reasoner;
	private JList bookmarkList;
	//private BookmarkListModel bookmarkListModel;
	private JTextPane queryPane;
	private JButton executeQuery;
	private JButton bookmarkBtn;
	private SwingResultModel resultModel;
	
	private Map<String, String> queryMap;
	private Preferences prefs;

	@Override
	protected void initialiseOWLView() throws Exception {
		prefs = PreferencesManager.getInstance().getApplicationPreferences(getClass());  
		
		initializeReasoner();
		initializeQueryMap();
		setLayout(new BorderLayout());
		add(createCenterComponent(), BorderLayout.CENTER);
		add(createBottomComponent(), BorderLayout.SOUTH);
	}
	
	private void initializeReasoner() {
		try {
			List<SparqlInferenceFactory> plugins = Collections.singletonList((SparqlInferenceFactory) new BasicSparqlReasonerFactory());
			reasoner = plugins.iterator().next().createReasoner(getOWLModelManager().getOWLOntologyManager());
			reasoner.precalculate();
		}
		catch (SparqlReasonerException e) {
			ErrorLogPanel.showErrorDialog(e);
		}
	}
	
	private void initializeQueryMap() {
		
		queryMap = new HashMap<String, String>();
		queryMap.put("bookmark test 1", reasoner.getSampleQuery());
		queryMap.put("bookmark test 2", "prefix ncit: <http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#>\n" + 
				"prefix owl:  <http://www.w3.org/2002/07/owl#>\n" + 
				"prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + 
				"SELECT distinct ?s ?l\n" + 
				"where { \n" + 
				"	?s ?p ?o .\n" + 
				"	?s a owl:Class .\n" + 
				"	optional {?s rdfs:label ?l .}\n" + 
				"	minus { ?s ncit:Semantic_Type ?x. }\n" + 
				"	}\n" + 
				"LIMIT 15");
	}
	private JComponent createCenterComponent() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(0,1));
		DefaultListModel<String> model = new DefaultListModel<>();
		Set<String> bookmarkKeys = queryMap.keySet();
		for(String bookmark : bookmarkKeys) {
			model.addElement(bookmark);
		}
		//model.addElement("bookmark test 1");
		//model.addElement("bookmark test 2");
		bookmarkList = new JList(model);
		queryPane = new JTextPane();
		
		bookmarkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		bookmarkList.setSelectedIndex(0);
		
		//String firstBookmarkKey = bookmarkKeys.iterator().next();
		queryPane.setText(queryMap.get(bookmarkList.getSelectedValue().toString()));
		
		bookmarkList.addListSelectionListener(new ListSelectionListener() {
			@Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                  queryPane.setText(queryMap.get(bookmarkList.getSelectedValue().toString()));
                }
            }
		});
		
		
		//queryPane.setText(reasoner.getSampleQuery());
		JScrollPane bookmarkSP = new JScrollPane(bookmarkList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		JScrollPane querySP = new JScrollPane(queryPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bookmarkSP, querySP);
		//splitPane.setDividerLocation(0.35);
		splitPane.setResizeWeight(0.5);
		panel.add(splitPane);
		resultModel = new SwingResultModel();
		BasicOWLTable results = new BasicOWLTable(resultModel) {
			private static final long serialVersionUID = 9143285439978520141L;

			@Override
			protected boolean isHeaderVisible() {
				return true;
			}
		};
		OWLCellRenderer renderer = new OWLCellRenderer(getOWLEditorKit());
		renderer.setWrap(false);
		results.setDefaultRenderer(Object.class, renderer);
		JScrollPane scrollableResults = new JScrollPane(results);
		panel.add(scrollableResults);
		return panel;
	}
	
	private JComponent createBottomComponent() {
		JPanel panel = new JPanel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER));
		executeQuery = new JButton("Execute");
		
		executeQuery.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					String query = queryPane.getText();
					long beg = System.currentTimeMillis();
					SparqlResultSet result = reasoner.executeQuery(query);
					System.out.println("The query took " + (System.currentTimeMillis() - beg));
					resultModel.setResults(result);
				}
				catch (SparqlReasonerException ex) {
					ErrorLogPanel.showErrorDialog(ex);
					JOptionPane.showMessageDialog(getOWLWorkspace(), ex.getMessage() + "\nSee the logs for more information.");
				}
			}
		});
		panel.add(executeQuery);
		
		bookmarkBtn = new JButton("Bookmark");
		bookmarkBtn.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					String query = queryPane.getText();
					String bookmark = JOptionPane.showInputDialog("Enter bookmark: ");
					if (bookmark != null && !bookmark.isEmpty()) {
						if(Utilities.isInteger(bookmark)) {
							if (Integer.parseInt(bookmark) != JOptionPane.CANCEL_OPTION) {
								saveBookmark(bookmark, query);
							}
						} else {
							saveBookmark(bookmark, query);
							//queryMap.put(bookmark, query);
							//System.out.println("bookmark is added. bookmark name = " + bookmark);
							//System.out.println("query = " + query);
						}
					}
				} catch (Exception ex) {
					ErrorLogPanel.showErrorDialog(ex);
					JOptionPane.showMessageDialog(getOWLWorkspace(), ex.getMessage() + "\nSee the logs for more information.");
				}
			}
		});
		panel.add(bookmarkBtn);
		return panel;
	}

	private void saveBookmark(String bookmark, String query) throws IOException {
		queryMap.put(bookmark, query);
		
		String filename = "." + File.separator + "bookmark" + File.separator + bookmark + ".txt";
		File file = new File(filename);
		file.getParentFile().mkdirs();
		FileWriter writer = new FileWriter(file);
		System.out.println(query);
		writer.write(query);
		writer.flush();
	    writer.close();
	}
	
	@Override
	protected void disposeOWLView() {
		if (reasoner != null) {
			reasoner.dispose();
			reasoner = null;
		}
	}
	
	

}
