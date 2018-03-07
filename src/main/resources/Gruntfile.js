module.exports = function(grunt) {
    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.initConfig({
        jshint: {
            all: ['Gruntfile.js', 'webroot/static/js/main.js']
        }
    });
};
