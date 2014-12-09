/*
 * Copyright 2014 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

define(['react', 'coreMixin', 'streamMixin'], function (React, coreMixin, streamMixin) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [coreMixin, streamMixin],

        componentName: function() { return "app/content/commons/StartStopButton/" + this.props.route; },

        getInitialState: function () {
            return {connected: false}
        },

        handleStop: function(e) {
            this.sendCommand(this.props.addr, this.props.route, "stop", {});
        },
        handleStart: function(e) {
            this.sendCommand(this.props.addr, this.props.route, "start", {});
        },

        render: function () {
            var self = this;


            var button;
            var buttonClasses;
            if (this.props.state == 'active') {
                buttonClasses = this.cx({
                    'disabled': (!self.state.connected),
                    'btn-default': true
                });
                button =
                    <button type="button" className={"btn btn-xs " + buttonClasses} onClick={this.handleStop}>
                    stop
                    </button>;
            } else {
                buttonClasses = this.cx({
                    'disabled': (!self.state.connected || this.props.state != 'passive'),
                    'btn-success': true
                });
                button =
                    <button type="button" className={"btn btn-xs " + buttonClasses} onClick={this.handleStart}>
                    start
                    </button>;
            }

            return button;
        }
    });

});