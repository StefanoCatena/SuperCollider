AmbiXY
{
	//
	var <>id, <>type;
	var synth, <>bus, vol;
	var xy; //between -1, 1
	var <>room;
	//var revMix;
	*new{ |id, type, x = 1, y = 1|
		^super.new.init8ch(id, type, x, y); //forces the evaluation of init8ch as soon as the class is called
	}

	init8ch { |anId, aType, anX, anY|
		id = anId;
		type = aType; //8ch or bin
		Server.local.waitForBoot{
			case
			{type == "8ch"}{SynthDef(\spat,
				{ |in, amp = 1, x = 1, y = 1, revMix, damp = 0.5, room = 100|
					var w1, x1, y1, z1;
					//revMix = ((x*x)+(y*y)).sqrt.linexp(0.1, 2.5, 0.1, 1);
					revMix = ((x*x)+(y*y)).sqrt.linexp(0.1, 5, 0.1, 1);
					//[x, y].poll ;
					#w1, x1, y1, z1 = BFEncode2.ar(In.ar(in), x, y, 0, 1);
					//Out.ar(0, BFDecode1.ar(w1, x1, y1, z1, [0, pi/4, pi/2, 2.3561944901923, pi, -2.3561944901923, -pi/2, -pi/4], 0));
					Out.ar(0, FreeVerb.ar(BFDecode1.ar(w1, x1, y1, z1,  [0, pi/4, pi/2, 2.3561944901923, pi, -2.3561944901923, -pi/2, -pi/4], 0), revMix, room, damp));
					//Out.ar(0, GVerb.ar(BFDecode1.ar(w1, x1, y1, z1, [0, pi/4, pi/2, 2.3561944901923, pi, -2.3561944901923, -pi/2, -pi/4], 0), room, revMix, damp, maxroomsize:20)); //added reverb
			}).add;
			//"8 CANALI!!".postln;

			} //synth effettivo
			{type == "bin"}{SynthDef(\spat,
				{ |in, amp = 1, x = 1, y = 1, revMix, damp = 0.8, room = 100 |
					var w1, x1, y1, z1;
					revMix = ((x*x)+(y*y)).sqrt.linexp(0.1, 5, 0.1, 1);
					//[x, y].poll ;
					#w1, x1, y1, z1 = BFEncode2.ar(In.ar(in), x, y, 0, 1);
					//Out.ar(0, BFDecode1.ar(w1, x1, y1, z1,  [-0.5pi, 0.5pi], 0));
					//Out.ar(0, FreeVerb.ar(BFDecode1.ar(w1, x1, y1, z1,  [-0.5pi, 0.5pi], 0), revMix, room, damp));
					Out.ar(0, GVerb.ar(BFDecode1.ar(w1, x1, y1, z1, [-0.5pi, 0.5pi], 0), room, revMix, damp, earlyreflevel: 0.707, taillevel: revMix.linexp(0.1, 5, 0.6, 0.9), maxroomsize:100));
					//Out.ar(0, JPverb.ar(BFDecode1.ar(w1, x1, y1, z1, [-0.5pi, 0.5pi], 0), revMix, damp, 0.5, 0.707));

			}).add; //added reverb

			//"STEREO!!".postln;
			}
			{type == "4ch"}{SynthDef(\spat,
				{ |in, amp = 1, x = 1, y = 1, revMix, damp = 0.5, room = 100|
					var w1, x1, y1, z1;
					//revMix = ((x*x)+(y*y)).sqrt.linexp(0.1, 2.5, 0.1, 1);
					revMix = ((x*x)+(y*y)).sqrt.linexp(0.1, 5, 0.1, 1);
					//[x, y].poll ;
					#w1, x1, y1, z1 = BFEncode2.ar(In.ar(in), x, y, 0, 1);
					//Out.ar(0, BFDecode1.ar(w1, x1, y1, z1, [0, pi/4, pi/2, 2.3561944901923, pi, -2.3561944901923, -pi/2, -pi/4], 0));
					Out.ar(0, FreeVerb.ar(BFDecode1.ar(w1, x1, y1, z1,  [-0.25pi, 0.25pi, 0.75pi, 1.25pi], 0), revMix, room, damp));
			}).add;

			}; //synth effettivo
			bus = Bus.audio(Server.local, 1); //bus entrata audio
			Server.local.sync;
			synth = Synth(\spat, [\in, bus], addAction:\addToTail); //definizione entrata audio
			this.xy_(anX, anY);
		}
	}

	xy_{|newX, newY|
		xy = [newX, newY];
		this.changed(\xy, [id, xy]); //notificatore di cambiamenti: manda ad ogni classe registrata con metodo update informazioni dicendo che qualcosa è cambiato
		synth.set(\x, xy[0], \y, xy[1]);
	}

	xy {^xy}

	synth {^synth}

	synth_{|aSynth|
		synth = aSynth;
		synth.get(\amp, {|v| vol = v.ampdb;})
	}

	vol_ {|aVol| vol = aVol ; // in dB
		synth.set(\amp, vol.dbamp)
	}
	vol {^vol}

	pause {synth.run(false)}
	play {synth.run}

	mute {
		synth.get(\amp, {|v|
			vol = v.ampdb;
			synth.set(\amp, 0) }) ;
	}
	unmute { synth.set(\amp, vol.dbamp) }
	kill { synth.free }

}

AmbiPlot {
	var <> ambi;
	var <>or, <>dim, <>ptD;
	var <>window;
	var <>step;

	*new{ |anAmbi|
		^super.new.initAmbiPlot(anAmbi);
	}

	initAmbiPlot{
		|ambiSrc|
		case
		{ambiSrc.class != Array}{ambi = [ambiSrc]}
		{ambiSrc.class == Array}{ambi = ambiSrc};
		dim = 300;
		ptD = 10;
		or = Point(dim, dim);
		ambi.do{|i| i.addDependant(this)};
		this.createWindow;
	}

	createWindow{
		var col = 0.05;
		window = Window("Plot", Rect(100, 100, dim*2, dim*2))
		.background_(Color.black)
		.drawFunc_{
			ambi.do{ //per ogni sorgente cicla
				|i, id|
				Pen.fillColor_(Color.hsv(col*id, 0.9, 1)) ; //colore
				Pen.fillOval(
					Rect(
						i.xy[0]*dim-(ptD*0.5)+or.x, //posizione x della sorgente * dimensione - (metà del diametro?) + x di or
						i.xy[1].neg*dim-(ptD*0.5)+or.y,
						ptD, ptD))
			}
		}
		.front.alwaysOnTop_(true);
	}

	newSrc{
		|aNewSrc|
		ambi = ambi.add(aNewSrc);
		ambi.postln;
		ambi.do{|i| i.addDependant(this)};
		window.refresh;
	}

	update { arg theChanged, theChanger, more;
		window.refresh;
	}

}
