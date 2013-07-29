var getLayer = function(url,attrib) {
    return L.tileLayer(url, { maxZoom: 18, attribution: attrib });
};


/*
 * L.TileLayer.WMS is used for putting WMS tile layers on the map.
 */

L.TileLayer.MYWMS = L.TileLayer.extend({

  options: {
    enableCanvas: true,
    unloadInvisibleTiles: true
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

    this._crs = this.options.crs || map.options.crs;

    var projectionKey = parseFloat(this.wmsParams.version) >= 1.3 ? 'crs' : 'srs';
    this.wmsParams[projectionKey] = this._crs.code;

    this.on("tileunload", function(d) {
      console.log("tileunload");
      console.log("tileunload, d is: " + d);
      if (d.tile._interval != null && d.tile._interval != -1) {
        console.log("unloading interval");
        window.clearInterval(d.tile._interval);
      }
    });

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
  },
  _tileOnUnload: function() {
    console.log("tile on unload");
  },
 
  _tileOnLoad: function () {
    console.log("tileOnLod");
    if (this._layer.options.enableCanvas && !this.canvasContext) {
      var canvas = document.createElement("canvas");
      canvas.width = canvas.height = this._layer.options.tileSize;
      this.canvasContext = canvas.getContext("2d");
    }

    var ctx = this.canvasContext;
    if (ctx) {
      var foothis = this;
      foothis.onload  = null; // to prevent an infinite loop
      ctx.drawImage(foothis, 0, 0);
      var imgd = ctx.getImageData(0, 0, foothis._layer.options.tileSize, foothis._layer.options.tileSize);
      var pix = imgd.data;

      
      var backPix = [];
      var dataPresent = false 
      for (var i = 0, n = pix.length; i < n; i += 4) {
        backPix[i] = pix[i];
        backPix[i+1] = pix[i+1];
        backPix[i+2] = pix[i+2];
        var alpha = pix[i+3];
        backPix[i+3] = alpha;
        if (alpha != 0) {
          dataPresent = true;
        } 
      }
      // save original data
      /*var backCanvas = document.createElement('canvas');
      backCanvas.width = canvas.width;
      backCanvas.height = canvas.height;
      var backCtx = backCanvas.getContext('2d'); 
      backCtx.drawImage(foothis, 0, 0);
      backImgd = backCtx.getImageData(0, 0, this._layer.options.tileSize, this._layer.options.tileSize)
      backPix = backImgd.data
      */ 
    // Refresh image layer
    var oldThreshold = 0; //travelTimeViz.getTime(); 
    if (! dataPresent) {

    } else {
      console.log("this tile has data");
    foothis._interval = window.setInterval(function() {
      /*var ctx = foothis.canvasContext
      console.log("tile ping");
      ctx.drawImage(foothis, 0, 0);
      var newimgd = ctx1.getImageData(0, 0, foothis._layer.options.tileSize, foothis._layer.options.tileSize);
      var pix = origimgd.data;
*/ 
      var threshold = travelTimeViz.getTime();
      if (oldThreshold != threshold) {
        oldThreshold = threshold;
      ctx.drawImage(foothis, 0, 0);

      //console.log("ping");
      //console.log("threshold: " + threshold); 
      for (var i = 0, n = pix.length; i < n; i += 4) {
        var green = backPix[i + 1];
        var alpha = backPix[i + 3];
        var blue = backPix[i + 2];
        var red = backPix[i];

        var time = (green * (255)) + red + blue //blue * (255 * 255) + green * (255) +red 
        if (false && i % 100) {
          console.trace("red: " + red)
          console.trace("green: " + green)
          console.trace("blue: " + blue)
          console.trace("alpha: " + alpha)
        }
        var color = 0  
        if ((time > threshold || alpha == 0)) {
          pix[i] = color;
          pix[i + 1] = color;
          pix[i + 2] = color;
          pix[i + 3] = 0;
        } else {
          pix[i] = 0;
          pix[i + 1] = 0;
          pix[i + 2] = 0;
          pix[i + 3] = 255;
        }
        //pix[i + 2] = 0;
        //pix[i + 1] = 0;
        //pix[i] = 0;
        //pix[i + 1] = 0;
        //pix[i] = 0;
        //pix[i + 1] = 255;
        //pix[i + 2] = 0;
        //pix[i + 3] = 255;

        //pix[i] = pix[i + 1] = pix[i + 2] = (3 * pix[i] + 4 * pix[i + 1] + pix[i + 2]) / 8;
      }
      ctx.putImageData(imgd, 0, 0);
      foothis.removeAttribute("crossorigin");
      foothis.src = ctx.canvas.toDataURL();
    }}, 1 * 50);
    }
   } 
{
    L.TileLayer.prototype._tileOnLoad.call(this);
  }
}});

L.tileLayer.mywms = function (url, options) {
  return new L.TileLayer.MYWMS(url, options);
};

var Layers = {
    stamen: { 
        toner:  'http://{s}.tile.stamen.com/toner/{z}/{x}/{y}.png',   
        terrain: 'http://{s}.tile.stamen.com/terrain/{z}/{x}/{y}.png',
        watercolor: 'http://{s}.tile.stamen.com/watercolor/{z}/{x}/{y}.png',
        attrib: 'Map data &copy;2013 OpenStreetMap contributors, Tiles &copy;2013 Stamen Design'
    },
    mapBox: {
        azavea:     'http://{s}.tiles.mapbox.com/v3/azavea.map-zbompf85/{z}/{x}/{y}.png',
        worldGlass:     'http://{s}.tiles.mapbox.com/v3/mapbox.world-glass/{z}/{x}/{y}.png',
        worldBlank:  'http://{s}.tiles.mapbox.com/v3/mapbox.world-blank-light/{z}/{x}/{y}.png',
        worldLight: 'http://{s}.tiles.mapbox.com/v3/mapbox.world-light/{z}/{x}/{y}.png',
        attrib: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="http://mapbox.com">MapBox</a>'
    }
};

var map = (function() {
    var selected = getLayer(Layers.mapBox.azavea,Layers.mapBox.attrib);

    var baseLayers = {
        "Azavea" : selected,
        "World Light" : getLayer(Layers.mapBox.worldLight,Layers.mapBox.attrib),
        "Terrain" : getLayer(Layers.stamen.terrain,Layers.stamen.attrib),
        "Watercolor" : getLayer(Layers.stamen.watercolor,Layers.stamen.attrib),
        "Toner" : getLayer(Layers.stamen.toner,Layers.stamen.attrib),
        "Glass" : getLayer(Layers.mapBox.worldGlass,Layers.mapBox.attrib),
        "Blank" : getLayer(Layers.mapBox.worldBlank,Layers.mapBox.attrib)
    };

    var m = L.map('map').setView([39.9886950160466,-75.1519775390625], 10);

    selected.addTo(m);
    
    m.lc = L.control.layers(baseLayers).addTo(m);

    $('#map').resize(function() {
        m.setView(m.getBounds(),m.getZoom());
    });

    return m;
})();

var travelTimeViz = (function() {
  var mapLayer = null
  var time = 8 * 60 * 60;
  return {
    setDuration: function(o) {
      console.log("time set to... " + o)
      time = o;
      mapLayer.refresh();
    },
    setMapLayer: function(o) {
      mapLayer = o;
    },
    getTime: function(o) {
      return time;

    }
  }
})()

var travelTimes = (function() {
    var mapLayer = null;
    var vectorLayer = null;
    var opacity = 0.7;

    var duration = 10*60;
    var time = 8*60*60;

    return {
        setOpacity : function(o) {
            opacity = o;
            if(mapLayer) { 
                mapLayer.setOpacity(opacity); 
            }
        },
        setTime : function(o) {
            time = o;
            travelTimes.update();
        },
        setDuration : function(o) {
            duration = o;
            travelTimes.update();
        },
        update : function() {
            $.ajax({
                url: 'gt/travelshed/request',
                data: { latitude: startMarker.getLat(),
                        longitude: startMarker.getLng(),
                        time: time,
                        duration: duration,
                        colorRamp: colorRamps.getColorRamp(),
                        format: 'image/png' },
                dataType: "json",
                success: function(data) {
                    if (mapLayer) {
                        map.lc.removeLayer(mapLayer);
                        map.removeLayer(mapLayer);
                        mapLayer = null;
                    }
                    if (vectorLayer) {
                        map.lc.removeLayer(vectorLayer);
                        map.removeLayer(vectorLayer);
                        vectorLayer = null; 
                    }
//                    if(data.extent) {
                        //extent = data.extent;
                        //url = data.;
                      if(data.token) {
                          token = data.token
//                        mapLayer = new L.ImageOverlay(url, extent);
                        mapLayer = new L.TileLayer.MYWMS("gt/travelshed/wms", {
                            token: token,
                            // lat: startMarker.getLat(),
                            // lng: startMarker.getLng(),
                            // time: time,
                            // duration: duration,
                            // format: 'image/png',
                            // transparent: true,
                            colorRamp: colorRamps.getColorRamp(),
                            attribution: 'Azavea',
                            datapng: 'true'
                        })
                        travelTimeViz.setMapLayer(mapLayer);

                        //mapLayer.setOpacity(0);
                        mapLayer.setOpacity(opacity);
                        mapLayer.addTo(map);
                        map.lc.addOverlay(mapLayer, "Travel Times");

                        var canvasTiles = L.tileLayer.canvas();

/*
                        canvasTiles.drawTile = function(canvas, tilePoint, zoom) {
                            var ctx = canvas.getContext('2d');
                            ctx.strokeStyle = ctx.fillStyle = "red";
                            ctx.rect(0,0, 256,256);
                            ctx.stroke();
                            ctx.fillText('(' + tilePoint.x + ', ' + tilePoint.y + ')',5,10);
                        };
                        map.lc.addOverlay(canvasTiles, "Tile Grid");
*/

/*
                        $.ajax({
                          url: 'gt/travelshed/json',
                          data: { token: token },
                          success: function(data) {
                            vectorLayer = L.geoJson().addTo(map);
                            vectorLayer.addData(data); 
                          }
                        })
 */                       
                    }
                }
            });
        }
    }
})();

var startMarker = (function() {
    var lat = 40.0175
    var lng = -75.059

    var marker = L.marker([lat,lng], {
        draggable: true 
    }).addTo(map);
    
    marker.on('dragend', function(e) { 
        lat = marker.getLatLng().lat;
        lng = marker.getLatLng().lng;
        travelTimes.update();
    } );

    return {
        getMarker : function() { return marker; },
        getLat : function() { return lat; },
        getLng : function() { return lng; }
    }
})();

var colorRamps = (function() {
    var colorRamp = "blue-to-red";

    var makeColorRamp = function(colorDef) {
        var ramps = $("#color-ramp-menu");

        var p = $("#colorRampTemplate").clone();
        p.find('img').attr("src",colorDef.image);
        p.click(function() {
            $("#activeRamp").attr("src",colorDef.image);
            colorRamps.setColorRamp(colorDef.key);
        });
        if(colorDef.key == colorRamp) {
            $("#activeRamp").attr("src",colorDef.image);
        }
        p.show();
        ramps.append(p);
    }

    return { 
        bind: function() {
            $.ajax({
                url: 'gt/admin/colors',
                dataType: 'json',
                success: function(data) {
                    _.map(data.colors, makeColorRamp)
                }
            });
        },
        setColorRamp: function(key) { 
            colorRamp = key;
            travelTimes.update();
        },
        getColorRamp: function(key) { 
            return colorRamp;
        },
    };
})();

var opacitySlider = (function() {
    var opacitySlider = $("#opacity-slider").slider({
        value: 0.7,
        min: 0,
        max: 1,
        step: .02,
        slide: function( event, ui ) {
            travelTimes.setOpacity(ui.value);
        }
    });

    return {
        setOpacity: function(o) {
            opacitySlider.slider('value', o);
        }
    }
})();

var timeSlider = (function() {
    var slider = $("#time-slider").slider({
        value: 10*60*60,
        min: 0,
        max: 24*60*60,
        step: 10,
        change: function( event, ui ) {
            travelTimes.setTime(ui.value);
        }
    });

    return {
        setTime: function(o) {
            slider.slider('value', o);
        }
    }
})();

var durationSlider = (function() {
    var slider = $("#duration-slider").slider({
        value: 30*60,
        min: 0,
        max: 45*60,
        step: 1,
        change: function( event, ui ) {
            travelTimes.setDuration(ui.value);
        }
    });

    return {
        setDuration: function(o) {
            slider.slider('value', o);
        }
    }
})();

var durationVizSlider = (function() {
    var slider = $("#duration-viz-slider").slider({
        value: 30*60,
        min: 0,
        max: 45*60,
        step: 5,
        //animate: true,
        slide: function( event, ui ) {
            travelTimeViz.setDuration(ui.value);
        }
    });

    return {
        setDuration: function(o) {
            slider.slider('value', o);
        }
    }
})();


var setupSize = function() {
    var bottomPadding = 10;

    var resize = function(){
        var pane = $('#left-pane');
        var height = $(window).height() - pane.position().top - bottomPadding;
        pane.css({'height': height +'px'});

        var mapDiv = $('#map');
        var height = $(window).height() - mapDiv.offset().top - bottomPadding;
        mapDiv.css({'height': height +'px'});

        map.invalidateSize();
    };
    resize();
    $(window).resize(resize);
};

// On page load
$(document).ready(function() {
    setupSize();
    colorRamps.bind();
    travelTimes.update();
});
