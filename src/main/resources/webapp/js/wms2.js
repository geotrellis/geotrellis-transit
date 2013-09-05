/*
 * L.TileLayer.WMS is used for putting WMS tile layers on the map.
 */

L.TileLayer.WMS2 = L.TileLayer.Canvas.extend({

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
	
	L.setOptions(this, options);
    },
    
    drawTile: function(canvas, tilePoint) {
	var ts = this.options.tileSize;
	var ctx = canvas.getContext('2d');
	var _this = this;
	var imageObj = new Image();
       
	var setPattern = function() {
	    var pattern = ctx.createPattern(imageObj, "repeat");
	    ctx.beginPath();
            ctx.rect(0, 0, canvas.width, canvas.height);
            ctx.fillStyle = pattern;
            ctx.fill();
	};
        

	var dataUrl = this.getTileUrl(tilePoint) + "&datapng=true";
	imageObj.onload = function() {
	    
	    // get datapng
	    //setTimeout(setPattern,0);
	    var dataObj = new Image();
	    dataObj.onload = function() {
		_this.animateTile(ctx, imageObj, dataObj); 
	    }
	    dataObj.src = dataUrl;
	}

	this._adjustTilePoint(tilePoint);
	imageObj.src = this.getTileUrl(tilePoint);
    },

    animateTile: function (ctx, imageObj, dataObj) {
	var pattern = ctx.createPattern(imageObj, "repeat");
	ctx.beginPath();
        ctx.rect(0, 0, 256, 256); //canvas.width, canvas.height);
        ctx.fillStyle = pattern;
        ctx.fill();
    },

    onAdd: function (map) {
	
	this._crs = this.options.crs || map.options.crs;
	
	var projectionKey = parseFloat(this.wmsParams.version) >= 1.3 ? 'crs' : 'srs';
	this.wmsParams[projectionKey] = this._crs.code;
	
	L.TileLayer.prototype.onAdd.call(this, map);
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
	
	return url + L.Util.getParamString(this.wmsParams, url, true) + '&bbox=' + bbox;
    },
    
    setParams: function (params, noRedraw) {
	
	L.extend(this.wmsParams, params);
	
	if (!noRedraw) {
	    this.redraw();
	}
	
	return this;
    }
});

L.tileLayer.wms2 = function (url, options) {
    return new L.TileLayer.WMS(url, options);
};
