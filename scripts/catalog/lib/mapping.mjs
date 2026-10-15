// Mapping from free-exercise-db values to Haltera enums (spec §3.1).

export const CATEGORIES = ['STRENGTH', 'CARDIO', 'MOBILITY', 'PLYOMETRICS', 'CALISTHENICS', 'SPORT'];
export const TRACKING_TYPES = ['WEIGHT_REPS', 'BODYWEIGHT_REPS', 'REPS', 'TIME', 'WEIGHT_TIME', 'DISTANCE_TIME'];
export const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'EXPERT'];
export const MUSCLES = [
  'ABDOMINALS', 'ABDUCTORS', 'ADDUCTORS', 'BICEPS', 'CALVES', 'CHEST', 'FOREARMS', 'GLUTES', 'HAMSTRINGS',
  'LATS', 'LOWER_BACK', 'MIDDLE_BACK', 'NECK', 'QUADRICEPS', 'SHOULDERS', 'TRAPS', 'TRICEPS', 'FULL_BODY',
];
export const EQUIPMENT = [
  'BARBELL', 'EZ_BAR', 'TRAP_BAR', 'DUMBBELL', 'KETTLEBELL', 'SMITH', 'MACHINE', 'CABLE', 'BAND', 'BODYWEIGHT',
  'MEDICINE_BALL', 'SWISS_BALL', 'FOAM_ROLLER', 'CARDIO_MACHINE', 'OTHER', 'NONE',
];
export const LOAD_TYPES = ['BARBELL', 'DUMBBELL', 'MACHINE', 'BODYWEIGHT', 'BAND', 'NONE'];

/** Load types each equipment may use; the first one is the default. */
export const ALLOWED_LOAD_TYPES = {
  BARBELL: ['BARBELL'],
  EZ_BAR: ['BARBELL'],
  TRAP_BAR: ['BARBELL'],
  SMITH: ['BARBELL', 'MACHINE'],
  DUMBBELL: ['DUMBBELL'],
  KETTLEBELL: ['DUMBBELL'],
  MEDICINE_BALL: ['DUMBBELL', 'NONE'],
  MACHINE: ['MACHINE'],
  CABLE: ['MACHINE'],
  BAND: ['BAND'],
  BODYWEIGHT: ['BODYWEIGHT', 'NONE'],
  SWISS_BALL: ['NONE'],
  FOAM_ROLLER: ['NONE'],
  CARDIO_MACHINE: ['NONE'],
  OTHER: ['NONE', 'DUMBBELL', 'BARBELL', 'MACHINE', 'BODYWEIGHT', 'BAND'],
  NONE: ['NONE', 'BODYWEIGHT'],
};

const SOURCE_EQUIPMENT = {
  'barbell': 'BARBELL',
  'e-z curl bar': 'EZ_BAR',
  'dumbbell': 'DUMBBELL',
  'kettlebells': 'KETTLEBELL',
  'machine': 'MACHINE',
  'cable': 'CABLE',
  'bands': 'BAND',
  'body only': 'BODYWEIGHT',
  'medicine ball': 'MEDICINE_BALL',
  'exercise ball': 'SWISS_BALL',
  'foam roll': 'FOAM_ROLLER',
  'other': 'OTHER',
};

const SOURCE_CATEGORY = {
  'strength': 'STRENGTH',
  'powerlifting': 'STRENGTH',
  'olympic weightlifting': 'STRENGTH',
  'strongman': 'STRENGTH',
  'stretching': 'MOBILITY',
  'plyometrics': 'PLYOMETRICS',
  'cardio': 'CARDIO',
};

export function categoryOf(source) {
  const category = SOURCE_CATEGORY[source.category];
  if (!category) throw new Error(`Unknown source category "${source.category}" in ${source.name}`);
  return category;
}

/** `category` is the resolved (possibly patched) Haltera category; it picks the default for null equipment. */
export function equipmentOf(source, category = categoryOf(source)) {
  if (/smith machine/i.test(source.name)) return 'SMITH';
  if (source.equipment === 'machine' && source.category === 'cardio') return 'CARDIO_MACHINE';
  if (source.equipment == null) return category === 'STRENGTH' ? 'BODYWEIGHT' : 'NONE';
  const equipment = SOURCE_EQUIPMENT[source.equipment];
  if (!equipment) throw new Error(`Unknown source equipment "${source.equipment}" in ${source.name}`);
  return equipment;
}

export function loadTypeOf(equipment) {
  return ALLOWED_LOAD_TYPES[equipment][0];
}

export function trackingOf(category, equipment, force) {
  if (category === 'MOBILITY') return 'TIME';
  if (category === 'CARDIO') return 'DISTANCE_TIME';
  const loadType = loadTypeOf(equipment);
  if (category === 'PLYOMETRICS') return loadType === 'NONE' || loadType === 'BODYWEIGHT' ? 'REPS' : 'WEIGHT_REPS';
  if (equipment === 'BODYWEIGHT') return force === 'static' ? 'TIME' : 'BODYWEIGHT_REPS';
  if (loadType === 'NONE') return force === 'static' ? 'TIME' : 'REPS';
  return 'WEIGHT_REPS';
}

export function musclesOf(list) {
  return list.map((m) => {
    const value = m.toUpperCase().replace(/ /g, '_');
    if (!MUSCLES.includes(value)) throw new Error(`Unknown muscle "${m}"`);
    return value;
  });
}

export function levelOf(level) {
  return level ? level.toUpperCase() : 'BEGINNER';
}
