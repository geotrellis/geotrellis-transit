var getLayer = function(url,attrib) {
    return L.tileLayer(url, { maxZoom: 18, attribution: attrib });
};

var fps = 0, now, lastUpdate = (new Date)*1 - 1;

// The higher this value, the less the FPS will be affected by quick changes
// Setting this to 1 will show you the FPS of the last sampled frame only
var fpsFilter = 50;

var hexToRgb = function(hex) {
    var result = /^0x?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    var result = [ parseInt(result[1],16), parseInt(result[2],16), parseInt(result[3],16) ];
    return result;
}
var colorStrings = ["0x000000","0xF68481","0xFDB383","0xFEE085", 
                    "0xDCF288","0xB6F2AE","0x98FEE6","0x83D9FD",
                    "0x81A8FC","0x8083F7","0x7F81BD"];
var colorArray = _.map(colorStrings, hexToRgb);

var dataBreaks = [1,10,15,20,30,40,50,60,75,90,120]; 
var breakLength = dataBreaks.length;
var animationDelay = 75;

// green and blue channels to seconds
// red is unused because only needed for values > 18 hrs
var gbToSeconds = function(g,b) {
    var time = (g * (255)) + b 
    return time
}

var tileCounter = function() {
    var counter = 0;
    return {
	increment: function() { counter++; },
	decrement: function() { counter--; },
	getCounter: function() { counter }
    }
}();


/*
 * L.TileLayer.WMS is used for putting WMS tile layers on the map.
 */

L.TileLayer.DataWMS = L.TileLayer.extend({

    options: {
	    enableCanvas: true
//	    unloadInvisibleTiles: true
    },
    defaultWmsParams: {
	service: 'WMS',
	request: 'GetMap',
	version: '1.1.1',
	layers: '',
	styles: '',
	format: 'image/jpeg',
	transparent: false
    },

    initialize: function (url, options) { // (String, Object)

	this._url = url;
	this._interval = -1; 
	this._animate = true;
	
	var wmsParams = L.extend({}, this.defaultWmsParams),
        tileSize = options.tileSize || this.options.tileSize;
	
	if (options.detectRetina && L.Browser.retina) {
	    wmsParams.width = wmsParams.height = tileSize * 2;
	} else {
	    wmsParams.width = wmsParams.height = tileSize;
	}
	
	for (var i in options) {
	    // all keys that are not TileLayer options go to WMS params
	    if (!this.options.hasOwnProperty(i) && i !== 'crs') {
		wmsParams[i] = options[i];
	    }
	}
	
	this.wmsParams = wmsParams;
	
	var canvasEl = document.createElement('canvas');
	if( !(canvasEl.getContext && canvasEl.getContext('2d')) ) {
	    options.enableCanvas = false;
	}
	L.setOptions(this, options);
    },
    
    
    onAdd: function (map) {
	console.log("on add");
	this._crs = this.options.crs || map.options.crs;
	
	var projectionKey = parseFloat(this.wmsParams.version) >= 1.3 ? 'crs' : 'srs';
	this.wmsParams[projectionKey] = this._crs.code;
	
	this.on("tileunload", function(d) {
	    console.log("tileunload, with animate: " + d._animate);
	    //console.log("tileunload, d is: " + d);
	    if (d.tile._interval != null && d.tile._interval != -1) {
		console.log("unloading interval");
		window.clearInterval(d.tile._interval);
	    }
	    if (d.tile != null) {
		d.tile._animate = false;
	    }

	});

	this.on("remove", function(d) {
	    console.log("tile remove");
	    });
	L.TileLayer.prototype.onAdd.call(this, map);
    },
    
    onRemove: function (map) {
	console.log("onRemove");
	L.TileLayer.prototype.onRemove.call(this,map);
	for (var key in this._tiles) {
	    var tile = this._tiles[key];
	    tile._animate = false;
	    console.log("tile: " + tile);
	    //tile.onLoad();
	}

    },

    getTileUrl: function (tilePoint) { // (Point, Number) -> String
	
	var map = this._map,
	tileSize = this.options.tileSize,
	
	nwPoint = tilePoint.multiplyBy(tileSize),
	sePoint = nwPoint.add([tileSize, tileSize]),
	
	nw = this._crs.project(map.unproject(nwPoint, tilePoint.z)),
	se = this._crs.project(map.unproject(sePoint, tilePoint.z)),
	bbox = [nw.x, se.y, se.x, nw.y].join(','),
	
	url = L.Util.template(this._url, {s: this._getSubdomain(tilePoint)});
	
	return url + L.Util.getParamString(this.wmsParams, url, true) + '&bbox=' + bbox + '&datapng=true';
    },
    
    setParams: function (params, noRedraw) {
	
	L.extend(this.wmsParams, params);
	
	if (!noRedraw) {
	    this.redraw();
	}
	
	return this;
    },
    
    refresh: function() {
	for (var key in this._tiles) {
	    //var tile = this._tiles[key];
	    //tile.onLoad();
	}
    },
    
    _loadTile: function (tile, tilePoint) {
	tile.setAttribute('crossorigin', 'anonymous');
	L.TileLayer.prototype._loadTile.call(this, tile, tilePoint);
    },
    
    
    _unloadTile: function(tile, tilePoint) {
	console.log("_unloadTile");
	tile._animate = false;
    },
    _tileOnUnload: function() {
	console.log("tile on unload: " + this._animate);
	this._animate = false;
    },
    
    _tileOnLoad: function () {

	if (this.loadedBefore) {
	    console.log("I've been loaded before");
	} else {
	    console.log("I haven't been loaded before.");
	    this.loadedBefore = true;
	}
	L.TileLayer.prototype._tileOnLoad.call(this);
	console.log(this);
	if (this._layer.options.enableCanvas && !this.canvasContext) {
	    var canvas = document.createElement("canvas");
	    canvas.width = canvas.height = this._layer.options.tileSize;
	    this.canvasContext = canvas.getContext("2d");
	}
	var ctx = this.canvasContext;

	if ( ! ctx ) {
	    console.log("no canvas context found");
	} else {
	    var foothis = this;
	    foothis.onload  = null; // to prevent an infinite loop
	    this.onload = null;
	    ctx.drawImage(foothis, 0, 0);
	    var imgd = ctx.getImageData(0, 0, foothis._layer.options.tileSize, foothis._layer.options.tileSize);
	    var pix = imgd.data;
	    
	    var backPix = [];
	    var dataPresent = false; 

	    var maxSeconds = Number.MIN_VALUE;
	    var minSeconds = Number.MAX_VALUE;

	    for (var i = 0, n = pix.length; i < n; i += 4) {
		backPix[i] = pix[i];
		backPix[i+1] = pix[i+1];
		backPix[i+2] = pix[i+2];
		var alpha = pix[i+3];
		backPix[i+3] = alpha;
		var green = backPix[i + 1];
		var blue = backPix[i + 2];
		if (alpha != 0) {
 		   var seconds = (green * 255) + blue;
		   if (seconds < minSeconds) {
		       minSeconds = seconds;
		   } else if (seconds > maxSeconds) {
		       maxSeconds = seconds;
		   }
		}
	    }
	    
	    if (minSeconds != Number.MAX_VALUE) {
		dataPresent = true;
	    } 
	    // Refresh image layer

	    var oldThreshold = 0; //travelTimeViz.getTime(); 
	    var loaded = false;
	    if (dataPresent) {
		console.log("this tile has data: " + minSeconds + " - " + maxSeconds );
		console.log("now tiles active: " + tileCounter.getCounter())
		this._animate = true;
		var filterImage = function(threshold) {
		    if (oldThreshold != threshold) {
			oldThreshold = threshold;
			for (var i = 0, n = pix.length; i < n; i += 4) {
			    var green = backPix[i + 1];
			    var alpha = backPix[i + 3];
			    var blue = backPix[i + 2];
			    //var red = backPix[i];
			    
			    var time = (green * 255) + blue;
			    var color = 0;
	
			    if ((time > threshold || alpha == 0)) {
				pix[i] = color;
				pix[i + 1] = color;
				pix[i + 2] = color;
				pix[i + 3] = 0;
			    } else {
				var minutes = time / 60;
				var j = 0;
				while (j < breakLength - 1 && (minutes > dataBreaks[j])) {
				    j++;
				}
				var c = colorArray[j];
				pix[i] = c[0] ;
				pix[i + 1] = c[1] ;
				pix[i + 2] = c[2] ;
				pix[i + 3] = 255;
			    }
			}
				
				
			var newCanvas = document.createElement("canvas");
			newCanvas.width = newCanvas.height = foothis._layer.options.tileSize;
			newCtx = newCanvas.getContext("2d");

			ctx.putImageData(imgd, 0, 0);
			newCtx.drawImage(canvas, 0, 0);
			foothis.removeAttribute("crossorigin");
			foothis.src = newCtx.canvas.toDataURL();
		    }
		}
	    	
		var loaded = false;
		
		var counter = 0;
		//var fps = 10;
		var ms = 1000 / fps;

		var fps = 0, now, lastUpdate = (new Date)*1 - 1;

		// The higher this value, the less the FPS will be affected by quick changes
		// Setting this to 1 will show you the FPS of the last sampled frame only
		var fpsFilter = 50;

		filterImage(travelTimeViz.getTime());
		//L.TileLayer.prototype._tileOnLoad.call(this);
		loaded = true;
		tileCounter.increment();
		console.log("tile counter at: " + tileCounter.getCounter() );
		var animation = function() { 
		  

		    var thisFrameFPS = 1000 / ((now=new Date) - lastUpdate);
		    fps += (thisFrameFPS - fps) / fpsFilter;
		    lastUpdate = now;
		    
		    var threshold = travelTimeViz.getTime();
		   // if (loaded == false) {
		//	loaded = true;
		//	L.TileLayer.prototype._tileOnLoad.call(foothis);
		 //   }
		    filterImage(threshold);
		    // request an animation frame from the browser
		    // after a delay of 75 ms (fake frame rate)
		    if (foothis._animate) {
			setTimeout(function() {
			    requestAnimationFrame(animation);
		}	, animationDelay);
		    } else {
			console.log("ending animation!");
			tileCounter.decrement();
			
			console.log("tiles active; " + tileCounter.getCounter());
		    }
		};

		setInterval( function() {
		    console.log( fps.toFixed(1) + " fps");
		    },5000);

		animation();
	    }
	} 
    }	
  });

L.tileLayer.datawms = function (url, options) {
    return new L.TileLayer.MYWMS(url, options);
};


