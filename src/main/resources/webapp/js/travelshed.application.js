var APP = (function() {
    var Constants = (function() {
        var MAX_DURATION = 60 * 60;
        var d = new Date();
        var INITIAL_TIME = d.getTime() - d.setHours(0,0,0,0);

        //var baseUrl = "http://localhost:9999/api"
        var baseUrl = baseUrl || "http://207.245.89.247/api";

        var viewCoords = [39.9886950160466,-75.1519775390625];
        var geoCodeLowerLeft = { lat: 39.7353312333975, lng: -75.4468831918069 };
        var geoCodeUpperRight = { lat: 40.1696687666025, lng: -74.8802888081931 };

        var startLat = 39.950510086014404;   
        var startLng = -75.1640796661377;

        // For scenic route
        var destLat = 39.939751;
        var destLng = -75.162964;

        var breaks = 
            _.reduce(_.map([10,15,20,30,40,50,60,75,90,120], function(minute) { return minute*60; }),
                     function(s,i) { return s + "," + i.toString(); })

        var colors = "0xF68481,0xFDB383,0xFEE085," + 
            "0xDCF288,0xB6F2AE,0x98FEE6,0x83D9FD,0x81A8FC,0x8083F7,0x7F81BD"

        return {
            MAX_DURATION : MAX_DURATION,
            INITIAL_TIME : INITIAL_TIME,
            COLORS : colors,
            BREAKS : breaks,
            START_LAT : startLat,
            START_LNG : startLng,
            END_LAT : destLat,
            END_LNG : destLng,
            VIEW_COORDS : viewCoords,
            GEOCODE_LOWERLEFT : geoCodeLowerLeft,
            GEOCODE_UPPERRIGHT: geoCodeUpperRight,
            BASE_URL : baseUrl
        };
    })();

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

        var m = L.map('map').setView(Constants.VIEW_COORDS, 12);
        selected.addTo(m);

        m.lc = L.control.layers(baseLayers).addTo(m);

        $('#map').resize(function() {
            m.setView(m.getBounds(),m.getZoom());
        });

        return m;
    })();

    var requestModel = (function() {
        var listeners = [];
        var time = Constants.INITIAL_TIME;
        var duration = Constants.MAX_DURATION;
        var travelModes = ['walking'];
        var schedule = "weekday";
        var direction = "departing";
        var dynamicRendering = false;

        var notifyChange = function() { 
            _.each(listeners, function(f) { f(); });
        }

        return {
            onChange : function(f) {
                listeners.push(f);
            },
            setTime: function(newTime) {
                time = newTime;
                notifyChange();
            },
            getTime: function() {
                return time;
            },
            setDuration: function(newDuration) {
                duration = newDuration;
                notifyChange();
            },
            getDuration: function() {
                return duration;
            },
            addMode: function(mode) {
                if(!_.contains(travelModes,mode)) {
                    travelModes.push(mode);
                    notifyChange();
                };
            },
            removeMode: function(mode) {
                if(_.contains(travelModes,mode)) {
                    var i = travelModes.indexOf(mode);
                    travelModes.splice(i,1);
                    notifyChange();
                };
            },
            getModes: function() {
                return travelModes;
            },
            getModesString : function() {
                if(travelModes.length == 0) { return ""; }
                else {
                    return _.reduce(travelModes, 
                                    function(s,v) { return s + "," + v; });
                };
            },
            setSchedule: function(newSchedule) {
                schedule = newSchedule;
                notifyChange();
            },
            getSchedule: function() { 
                return schedule;
            },
            setDirection: function(newDirection) {
                direction = newDirection;
                notifyChange();
            },
            getDirection: function() { 
                return direction;
            },
            setDynamicRendering: function(newDynamicRendering) {
                dynamicRendering = newDynamicRendering;
                notifyChange();
            },
            getDynamicRendering: function() { 
                return dynamicRendering;
            }
        }
    })()

    var travelTimes = (function() {
        var mapLayer = null;
        var vectorLayer = null;
        var opacity = 0.9;

        return {
            setOpacity : function(o) {
                opacity = o;
                if(mapLayer) { 
                    mapLayer.setOpacity(opacity); 
                }
            },
            update : function() {
                var modes = requestModel.getModesString();
                if(modes != "") {
                    var time = requestModel.getTime();
                    var direction = requestModel.getDirection();
                    var schedule = requestModel.getSchedule();
                    var dynamicRendering = requestModel.getDynamicRendering()

                    if (mapLayer) {
                        map.lc.removeLayer(mapLayer);
                        map.removeLayer(mapLayer);
                        mapLayer = null;
                    }

		    if(dynamicRendering) {
                        var url = Constants.BASE_URL + "/travelshed/wmsdata";
		        mapLayer = new L.TileLayer.WMS2(url, {
                            getValue : function() { return requestModel.getDuration(); },
                            latitude: startMarker.getLat(),
                            longitude: startMarker.getLng(),
                            time: time,
                            duration: Constants.MAX_DURATION,
                            modes: modes,
                            schedule: schedule,
                            direction: direction,
                            breaks: Constants.BREAKS,
                            palette: Constants.COLORS,
                            attribution: 'Azavea'
		        });
		    } else {
                        var url = Constants.BASE_URL + "/travelshed/wms";
		        mapLayer = new L.TileLayer.WMS(url, {
                            latitude: startMarker.getLat(),
                            longitude: startMarker.getLng(),
                            time: time,
                            duration: requestModel.getDuration(),
                            modes: modes,
                            schedule: schedule,
                            direction: direction,
                            breaks: Constants.BREAKS,
                            palette: Constants.COLORS,
                            attribution: 'Azavea'
		        });
                    }
		    
		    mapLayer.setOpacity(opacity);
		    mapLayer.addTo(map);
		    map.lc.addOverlay(mapLayer, "Travel Times");
		    travelTimes.updateVector();
                }
            },
            updateVector : function() {

                if (vectorLayer) {
                    map.lc.removeLayer(vectorLayer);
                    map.removeLayer(vectorLayer);
                    vectorLayer = null; 
                }

                if($('#vector_checkbox').is(':checked')) {
                    var modes = requestModel.getModesString();
                    if(modes != "") {
                        var time = requestModel.getTime();
                        var duration = requestModel.getDuration();
                        var direction = requestModel.getDirection();
                        var schedule = requestModel.getSchedule();

                        $.ajax({
                            url: Constants.BASE_URL + '/travelshed/json',
                            dataType: "json",
                            data: { latitude: startMarker.getLat(),
                                    longitude: startMarker.getLng(),
                                    time: time,
                                    durations: duration,
                                    modes: modes,
                                    schedule: schedule,
                                    direction: direction },
                            success: function(data) {
                                if (vectorLayer) {
                                    map.lc.removeLayer(vectorLayer);
                                    map.removeLayer(vectorLayer);
                                    vectorLayer = null; 
                                }

                                var geoJsonOptions = {
                                    style: function(feature) {
                                        return {
                                            weight: 2,
                                            color: "#774C4A",
                                            opacity: 1,
                                            fillColor: "#9EFAE2",
                                            fillOpacity: 0.2
                                        };
                                    }
                                };

                                vectorLayer = 
                                    L.geoJson(data, geoJsonOptions)
                                    .addTo(map);
                            }
                        })
                    }
                }
            }
        }
    })();

    var startMarker = (function() {
        var lat = Constants.START_LAT;
        var lng = Constants.START_LNG;

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
            getLng : function() { return lng; },
            setLatLng : function(newLat,newLng) {
                lat = newLat;
                lng = newLng;
                marker.setLatLng(new L.LatLng(lat, lng));
                travelTimes.update();
            }
        }
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


    var endMarker = (function() {
        var lat = Constants.END_LAT;
        var lng = Constants.END_LNG;

        // Creates a red marker with the coffee icon
        var redMarker = L.AwesomeMarkers.icon({
            icon: 'coffee', 
            color: 'red'
        })

        var marker = L.marker([lat,lng], {
            draggable: true,
            icon: redMarker
        }).addTo(map);
        
        marker.on('dragend', function(e) { 
            lat = marker.getLatLng().lat;
            lng = marker.getLatLng().lng;
            travelTimes.update();
        });

        return {
            getMarker : function() { return marker; },
            getLat : function() { return lat; },
            getLng : function() { return lng; }
        }
    })();


    var timePicker = (function () {
        var now = new Date();
        requestModel.setTime(now.getSeconds() + now.getMinutes()*60 + now.getHours()*60*60);
        $('#time-picker').timepicker({ 'scrollDefaultNow': true }).timepicker('setTime', now);
        $('#time-picker').on('changeTime', function() {
            var value = $(this).timepicker('getSecondsFromMidnight');
	    requestModel.setTime(value);
            travelTimes.update();
        });
        
        return {
            setTime: function(o) {
                $('#time-picker').timepicker('getSecondsFromMidnight');
            }
        }
    })();

    var durationSlider = (function() {
        var slider = $("#duration-slider").slider({
            value: Constants.MAX_DURATION,
            min: 0,
            max: Constants.MAX_DURATION,
            step: 30,
            change: function( event, ui ) {      
	        if( ! $('#rendering_checkbox').is(':checked')) {
		    requestModel.setDuration(ui.value);
	        }
            },
	    slide: function (event, ui) {
	        requestModel.setDuration(ui.value);
	    }
        });

        return {
            setDuration: function(o) {
                slider.slider('value', o);
            }
        }
    })();

    var setupEvents = function() {
        $("#schedule-dropdown-menu li a").click(function(){
            var selText = $(this).text();
            $(this).parents('.dropdown').find('.dropdown-toggle').html(selText+' <span class="caret"></span>');
            requestModel.setSchedule(selText.toLowerCase());
        });

        $("#direction-dropdown-menu li a").click(function(){
            var selText = $(this).text();
            $(this).parents('.dropdown').find('.dropdown-toggle').html(selText+' <span class="caret"></span>');
            requestModel.setDirection(selText.toLowerCase());
            travelTimes.update();
        });
        
        $('#transit-types').find('label').tooltip({
            container: 'body',
            placement: 'bottom'
        });
        
        $('#toggle-sidebar-advanced').on('click', function() {
            $(this).toggleClass('active').next().slideToggle();
        });

        $("#transit_type").change(function() {
            travelTimes.update();
        });

        $("#schedule").change(function() {
            travelTimes.update();
        });

        $("#direction").change(function() {
            travelTimes.update();
        });

        $('#vector_checkbox').click(function() {
            travelTimes.updateVector();
        });

        $('#rendering_checkbox').click(function() {
	    if( $('#rendering_checkbox').is(':checked')) {
                requestModel.setDynamicRendering(true);
	        requestModel.setDuration(Constants.MAX_DURATION);
	    } else {
                requestModel.setDynamicRendering(false);
	        requestModel.setDuration(requestModel.getDuration());
	    }
	    travelTimes.update();
        });
    };

    var setupTransitModes = function() {
        $.each($("input[name='anytime-mode']"), function () {
            $(this).change(function() {
                if($(this).val() == 'walking') {
                    requestModel.removeMode("biking");
                    requestModel.addMode("walking");
                } else {
                    requestModel.removeMode("walking");
                    requestModel.addMode("biking");
                }
                travelTimes.update();
            });
        });

        $.each($("input[name='public-transit-mode']"), function () {
            $(this).change(function() {
                var val = $(this).val();
                if($(this).is(':checked')) {
                    requestModel.addMode(val);
                } else {
                    requestModel.removeMode(val);
                }
                travelTimes.update();
            });
        });
    };

    var Geocoder = (function(){
        var geocoder = null;
        return {
            onLoadGoogleApiCallback : function() {
                geocoder = new google.maps.Geocoder();
                document.body.removeChild(document.getElementById('load_google_api'));
            },
            setup : function() {
                var url = 
                    "https://maps.googleapis.com/maps/api/js?" + 
                    "v=3&callback=APP.onLoadGoogleApiCallback&sensor=false";
                var script = document.createElement('script');
                script.id = 'load_google_api';
                script.type = "text/javascript";
                script.src = url;
                document.body.appendChild(script);
            },
            geocode : function(address) {
                var lowerLeft = new google.maps.LatLng(Constants.GEOCODE_LOWERLEFT.lat, 
                                                       Constants.GEOCODE_LOWERLEFT.lng);
                var upperRight = new google.maps.LatLng(Constants.GEOCODE_UPPERRIGHT.lat, 
                                                        Constants.GEOCODE_UPPERRIGHT.lng);
                var bounds = new google.maps.LatLngBounds(lowerLeft, upperRight);

                var parameters = {
                    address: address,
                    bounds: bounds
                };

                var results = geocoder.geocode(parameters, function(data){
                    data = {results: data};

                    if (data.results.length == 0)
                        return [];
                    
                    for (var i = 0; i < data.results.length; i++) {
                        var lat = data.results[i].geometry.location.lat();
                        var lng = data.results[i].geometry.location.lng();
                        startMarker.setLatLng(lat,lng);
                    };
                });
            }
        };
    })();

    var wireUpAddressSearch = function() {
        $.each($("input[name='address']"), function() {
            var input = $(this);
            $(this).keypress(function (e) {
                if (e.which == 13) {
                    Geocoder.geocode(input.val());
                }
            });
            $(this).next().find('button').on('click', function() {
                Geocoder.geocode(input.val());
            });
        });
    };

    return {
        onLoadGoogleApiCallback : Geocoder.onLoadGoogleApiCallback,
        onReady : function() {
            Geocoder.setup();
            setupEvents();
            setupTransitModes();
            travelTimes.update();
            wireUpAddressSearch();
        }
    };
})();

// On page load
$(document).ready(function() {
    APP.onReady();
});
