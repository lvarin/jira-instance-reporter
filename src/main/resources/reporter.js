function populateTabData() {
	AJS.$
			.ajax({
				url : "/rest/reporter-rest/1.0/metric-manager/getNumberOfProjects",
				type : "GET",
				dataType : "json",
				success : function(nbProj) {
					AJS.$
							.ajax({
								url : "/rest/reporter-rest/1.0/metric-manager/getNumberOfIssues",
								type : "GET",
								dataType : "json",
								success : function(nbIssues) {
									document.getElementById("nb_proj").innerHTML = nbProj;
									document.getElementById("nb_issues").innerHTML = nbIssues;
								}
							});
				}
			});
};

// inspired by flotr2 examples
function drawGraphOverTime(list, container, title) {
	var d1 = [], graph, x = 0, o;

	for ( var i = 0; i < list.length; ++i) {
		x = x + 1;
		d1.push([ list[i], x ]);
	}
	// display trick to actually "see" the last entry on the graph
	d1.push([ list[list.length - 1] + (3600 * 24 * 60 * 30), x ]);
	var options = {
		xaxis : {
			mode : 'time',
			labelsAngle : 45
		},
		selection : {
			mode : 'x'
		},
		HtmlText : false,
		title : title
	};
	// Draw graph with default options, overwriting with passed options
	function drawGraph(opts) {

		// Clone the options, so the 'options' variable always keeps
		// intact.
		o = Flotr._.extend(Flotr._.clone(options), opts || {});

		// Return a new graph.
		return Flotr.draw(container, [ d1 ], o);
	}

	graph = drawGraph();

	Flotr.EventAdapter.observe(container, 'flotr:select', function(area) {
		// Draw selected area
		graph = drawGraph({
			xaxis : {
				min : area.x1,
				max : area.x2,
				mode : 'time',
				labelsAngle : 45
			},
			yaxis : {
				min : area.y1,
				max : area.y2
			}
		});
	});

	// When graph is clicked, draw the graph with default area.
	Flotr.EventAdapter.observe(container, 'flotr:click', function() {
		graph = drawGraph();
	});
};

function initReporter() {
	AJS.$.ajax({
		url : "/rest/reporter-rest/1.0/metric-manager/build",
		type : "GET",
		success : function(inutile) {
			populateTabProjects();
		}
	});
}

function populateTabProjects() {

	AJS.$.ajax({
		url : "/rest/reporter-rest/1.0/metric-manager/getProjectTimeList",
		type : "GET",
		dataType : "json",
		success : function(pList) {
			var container = document.getElementById("projects_graph");
			drawGraphOverTime(pList, document.getElementById("projects_graph"),
					"Number of projects over time");
		}
	});

};

function populateTabIssues() {
	AJS.$.ajax({
		url : "/rest/reporter-rest/1.0/metric-manager/getIssuesTimeList",
		type : "GET",
		dataType : "json",
		success : function(iList) {
			drawGraphOverTime(iList, document.getElementById("issues_graph"),
					"Number of issues over time");
		}
	});
};