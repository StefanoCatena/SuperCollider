DBAPSpeakerArray //a convinient class just to set the speakers position in x, y
{
	var <>arrayOfPositionsXY;
	*new
	{
		arg arrayOfPositionsXY = #[]; //-1, 1
		^super.new.initDBAPSpeakerArray(arrayOfPositionsXY);
	}
	initDBAPSpeakerArray
	{
		|positions|
		arrayOfPositionsXY = positions;
		^arrayOfPositionsXY;
	}
}

DBAPsrc //actual class for the single source
{
	var <>id, <>bus, <>dbapArr;
	var <>vi, <>dist, <>r;
	var <>synth;
	var <>a;
	var <>x, <>y, <>norm;

	*new
	{
		|id, x, y, dbapArr, r = 0.1|
		^super.new.initDBAPsrc(id, x, y, dbapArr, r);
	}


	initDBAPsrc
	{
		|anId, aX, aY, aDbapspkrArr, aR, aNorm|
		var rev;
		r = aR; //spatial blur coefficient
		id = anId; //id
		x = aX;
		y = aY;
		dbapArr = aDbapspkrArr; //x, y positions of the speakers as instance of DBAPSpeakerArray
		vi = Array.newClear(dbapArr.size);
		synth = Array.newClear(dbapArr.size);
		dist = Array.newClear(dbapArr.size);
		Server.local.waitForBoot
		{
			SynthDef(\dbapSpkr, { //speaker SynthDef
				arg in, amp, out, revMix, damp = 0.9, room = 1;
				var sig, wet;
				sig = In.ar(in)*amp; //scaled output of the calculed coefficient
				wet = FreeVerb.ar(sig, revMix, damp, room);
				Out.ar(out, wet);
			}).add;
			a = this.calcV(x, y); //coefficient for each speaker
			rev = this.calcRev(x, y);
			bus = Bus.audio(Server.local, 1);
			Server.local.sync;
			dbapArr.do({
				|item, i|
				synth[i] = Synth(\dbapSpkr, [\in, bus, \amp, a[i], \out, i, \revMix, rev], addAction:\addToTail); //synth for each speaker with right parameters
			});
			this.xy_(x, y);
		}
	}

	spkrArr{
		dbapArr.do({
			|item, i|
			"Speaker number:".scatArgs(i+1).postln;
			item.do({
				|jtem, j|
				case
				{j == 0}{"X:".scatArgs(jtem).postln}
				{j == 1}{"Y:".scatArgs(jtem).postln};
			});
			" ".postln;
		});
	}

	calcDist
	{
		|xS, yS, x, y, r|
		var d = ((((x-xS)**2)+((y-yS)**2))+(r**2)).sqrt;
		^d;
	}

	calcV
	{
		|x, y|
		var denK = 0, k;
		var a = 10**(-6.dbamp/20); //<--amplitude roll-off
		dbapArr.do({
			|item, i|
			dist[i] = this.calcDist(x, y, item[0], item[1], r);
			denK = denK + (1/(dist[i]**2));
		});
		k = (2*a)/(denK.sqrt); //<-- k, positional coefficient
		dbapArr.do({
			|item, i|
			var d;
			d = this.calcDist(x, y, item[0], item[1], r);
			vi[i] = k/(2*d*a); //<-- relative amplitude for the ith speaker
		});
		^vi; //<-- all of vi**2 sum together should be 1 (or 0.99999)
	}

	calcRev
	{
		|x, y|
		var revDist = ((x*x)+(y*y)).sqrt;
		var revMix;
		revMix = revDist.linexp(0, 1.4142135, 0.01, 1);
		//revMix = 0;
		^revMix
	}

	xy_{ //change the x, y coordinates of the src
		|newX, newY|
		var change = [], rev;
		change = this.calcV(newX, newY); //calculate again x and y
		rev = this.calcRev(newX, newY);
		dbapArr.do({
			|item, i|
			synth[i].set(\amp, change[i], \revMix, rev);
		});
		this.changed(\xy, [id, newX, newY]);
		^change;
	}

	nSpeakers{^dbapArr.size}

	xy{^[x, y]}

	returnID{^id}
}


DBAPPlot
{
	var <>dbapSrc, <>dbapArr;
	var <>dim = 300;
	var <>ptD = 15;
	var <>or;
	var <>window;
	var <>id, <>x, <>y;

	*new
	{
		|aDBAPSpeakerArray, aDBAP|
		^super.new.initDBAPPlot(aDBAPSpeakerArray, aDBAP);
	}

	initDBAPPlot
	{
		|dbapArray, dbap|
		case
		{dbap.class != Array}{dbapSrc = [dbap]}
		{dbap.class == Array}{dbapSrc = dbap};
		dbapArr = dbapArray;
		dbapArr.postln;
		x = Array.newClear(dbapSrc.size);
		y = Array.newClear(dbapSrc.size);
		or = Point(dim, dim);
		dbapSrc.do{|item, i| item.addDependant(this); x[i] = item.x; y[i] = item.y};
		this.createWindow;
	}

	createWindow
	{
		var col = 0.05;
		window = Window("Plot", Rect(100, 100, dim*2, dim*2))
		.background_(Color.black)
		.drawFunc_{
			dbapArr.do({
				|item, i|
				var rX, rY, rect;
				rect = 30;
				rX = item[0].linlin(-1.0, 1.0, rect*0.5, (dim*2)-(rect*1.5));
				rY = item[1].linlin(-1.0, 1.0, rect*0.5, (dim*2)-(rect*1.5));
				Pen.color_(Color.white);
				Pen.addRect(Rect(rX, rY, rect, rect));
				Pen.perform([\stroke]);
			});
			dbapSrc.do{
				|item, i|
				Pen.fillColor_(Color.hsv(col*i, 0.9, 1)) ; //probably color dies once i reaches a too big of a number
				Pen.fillOval(
					Rect(
						x[i]*dim-(ptD*0.5)+or.x,
						y[i].neg*dim-(ptD*0.5)+or.y,
						ptD,
						ptD);
				);
			};
		}.front.alwaysOnTop_(true);
	}

	newSrc{
		|aNewSrc|
		dbapSrc = dbapSrc.add(aNewSrc);
		x.add();
		y.add();
		dbapSrc.do{|item, i| item.addDependant(this); x[i] = item.x; y[i] = item.y;};
		window.refresh;
	}

	update {
		| theChanged, theChanger, upd|
		dbapSrc.do{
			|item, i|
			if (item.returnID == upd[0], {
				x[i] = upd[1];
				y[i] = upd[2];
			}
			)
		};
		window.refresh;
	}
}
/*
SynthDef(\test, { //actual synth
arg out, freq = 100;
var sig;
sig = SinOsc.ar(freq)*LFPulse.kr(1);
Out.ar(out, sig);
}).add;

x = DBAPSpeakerArray.new([[-1, 1], [1, 1], [1, -1], [-1, -1]]); //<-- set speakers position (this case quadriphony)

c = DBAPsrc.new(1, 1, 1, x, 0.01); // <-- id, initial x and y, DBAPSpeakerArray instance, spatial blur

Synth(\test, [\out, c.bus, \freq, 1000]); //<-- routes the synths out to the class' bus

c.xy_(-1, -0) //<-- new position of the source


c = Array.fill(5, {|i |DBAPsrc.new(i, rrand(-1.0, 1.0), rrand(-1.0, 1.0), x, 0.01);}); //<-- multiple sources
a = DBAPPlot.new(x, c);
5.do{|i| c[i].xy_(rrand(-1.0, 1.0), rrand(-1.0, 1.0))};

~trajectory = { //<-- trajectory function
arg x1, y1, x2, y2, //starting and ending point
dur, //duration
curve = \exp, //type of curve
src, //who to spatialize
sr = 0.01; //rate for movement
var xLenght = x2-x1 ; //total lenght of the trajectory
var numPoints = (dur/sr).asInteger ; //number of points of the envelope
var xStep = xLenght / numPoints ;
var env = Env([y1, y2], [1], curve).asSignal(numPoints).asArray; //the envelope
{
src.xy_(x1, y1);
env.do{ //the update routine
|i, j|
src.xy_(xStep*j+x1, i);
//updated values
sr.wait; //waits sr time
};
}.fork(AppClock)
};

~trajectory.(-0.5, 0.5, -0.5, -0.5, 5, \exp, z) <--some trajectories
~trajectory.(-1, -1, -1, 1, 5, \exp, c)
~trajectory.(-1, 1, 1, 1, 5, \exp, c)
~trajectory.(1, 1, -1, 1, 5, \exp, c)
~trajectory.(-1, 1, 1, -1, 5, \exp, c)
~trajectory.(1, -1, -1, 1, 5, \exp, c)

*/