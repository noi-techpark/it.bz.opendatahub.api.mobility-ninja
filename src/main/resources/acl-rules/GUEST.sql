-- OPEN DATA WHERE CLAUSE FOR THE GUEST ACCOUNT
-- This is the default role, which will be concatenated with a logical OR operator
-- to all other rules.
-- It defines OPEN DATA RULES seen by all guests, that is, people that are not logged in
(
    -- station types that are always open, regardless of the origin
	s.stationtype in (
		'NOI-Place',
		'CreativeIndustry',
		'BluetoothStation',
		'CarpoolingHub',
		'CarpoolingService',
		'CarpoolingUser',
		'CarsharingCar',
		'CarsharingStation',
		'EChargingPlug',
		'EChargingStation',
		'Streetstation',
		'ParkingSensor',
        'Culture')

    -- station types that are only partly open, constrained by the origin
	or (s.stationtype = 'Bicycle' and s.origin in ('ALGORAB', 'BIKE_SHARING_MERANO'))
	or (s.stationtype = 'BikesharingStation' and s.origin = 'ALGORAB')
	or (s.stationtype = 'EnvironmentStation' and s.origin = 'APPATN-open')
	or (s.stationtype = 'LinkStation' and (s.origin is null or s.origin = 'NOI'))
	or (s.stationtype = 'MeteoStation' and s.origin in ('meteotrentino', 'SIAG'))
	or (s.stationtype = 'ParkingStation' and s.origin in ('FAMAS', 'FBK', 'Municipality Merano'))
	or (s.stationtype = 'RWISstation' and s.origin = 'InfoMobility')

    -- special rules
	or (s.origin = 'APPABZ' and me.period = 3600)
)
