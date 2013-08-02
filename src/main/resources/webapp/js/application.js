var MAX_DURATION = 120 * 60
var INITIAL_TIME = 32400

// mins 
// maxs 

var getLayer = function(url,attrib) {
    return L.tileLayer(url, { maxZoom: 18, attribution: attrib });
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
        wnyc:       'http://{s}.tiles.mapbox.com/v3/jkeefe.map-id6ukiaw/{z}/{x}/{y}.png',
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
        "WNYC" : getLayer(Layers.mapBox.wnyc,Layers.mapBox.attrib),
        "World Light" : getLayer(Layers.mapBox.worldLight,Layers.mapBox.attrib),
        "Terrain" : getLayer(Layers.stamen.terrain,Layers.stamen.attrib),
        "Watercolor" : getLayer(Layers.stamen.watercolor,Layers.stamen.attrib),
        "Toner" : getLayer(Layers.stamen.toner,Layers.stamen.attrib),
        "Glass" : getLayer(Layers.mapBox.worldGlass,Layers.mapBox.attrib),
        "Blank" : getLayer(Layers.mapBox.worldBlank,Layers.mapBox.attrib)
    };

    // Philly
//    var m = L.map('map').setView([39.9886950160466,-75.1519775390625], 10);
    // NYC
    var m = L.map('map').setView([40.753499,-73.983994], 9);

    selected.addTo(m);

    m.lc = L.control.layers(baseLayers).addTo(m);

    $('#map').resize(function() {
        m.setView(m.getBounds(),m.getZoom());
    });

    // NY data extent
    //40.495526,-74.260025
    //40.920161,-73.688564

    //HEIGHT = 47191.92886399891
    //WIDTH = 48296.20594990707

    // Extent of OSM data - large
    // var polygon = L.polygon([
    //     [39.641,-75.572],
    //     [40.308,-75.572],
    //     [40.308,-74.641],
    //     [39.641,-74.641],
    // ],
    //   {  color: 'black',
    //     fillColor: '#f03',
    //     fillOpacity: 0.0}).addTo(m);

    // Extent of OSM data - med (same area as WNYC app)
    var polygon = L.polygon([
        // Philly
        // [39.7353312333975,-75.4468831918069],
        // [40.1696687666025,-75.4468831918069],
        // [40.1696687666025,-74.8802888081931],
        // [39.7353312333975,-74.8802888081931],
        
        // NYC
        [40.495526,-74.260025],
        [40.495526,-73.688564],
        [40.920161,-73.688564],
        [40.920161,-74.260025]
    ],
      {  color: 'black',
        fillColor: '#f03',
        fillOpacity: 0.0}).addTo(m);

    return m;
})();

var breaks = 
   _.reduce(_.map([1,10,15,20,30,40,50,60,75,90,120], function(minute) { return minute*60; }),
            function(s,i) { return s + "," + i.toString(); })

var colors = "0x000000,0xF68481,0xFDB383,0xFEE085,0xDCF288,0xB6F2AE,0x98FEE6,0x83D9FD,0x81A8FC,0x8083F7,0x7F81BD"

var travelTimes = (function() {
    var mapLayer = null;
    var vectorLayer = null;
    var opacity = 0.9;

    var duration = MAX_DURATION;
    var time = INITIAL_TIME;

    var vector_checkbox = $('#vector_checkbox')
    

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
                        duration: duration
                      },
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

                    if(data.token) {
                        token = data.token
                        mapLayer = new L.TileLayer.WMS("gt/travelshed/wms", {
                            token: token,
                            breaks: breaks,
                            palette: colors,
                            attribution: 'Azavea'
                        })

                        
                        mapLayer.setOpacity(opacity);
                        mapLayer.addTo(map);
                        map.lc.addOverlay(mapLayer, "Travel Times");

                        if($('input[name=vector_checkbox]').is(':checked')) {
                            $.ajax({
                                url: 'gt/travelshed/json',
                                data: { token: token },
                                success: function(data) {
                                    vectorLayer = L.geoJson().addTo(map);
                                    vectorLayer.addData(data); 
                                }
                            })
                        }
                        
                    }
                }
            });
        }
    }
})();

var startMarker = (function() {
    // Philly
//    var lat = 40.0175;    var lng = -75.059;
    // NYC
    var lat = 40.753499;  var lng = -73.983994

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
        value: 0.9,
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
        value: INITIAL_TIME,
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
        value: MAX_DURATION,
        min: 0,
        max: MAX_DURATION,
        step: 60,
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
