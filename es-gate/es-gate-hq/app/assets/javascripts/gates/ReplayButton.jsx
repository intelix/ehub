/*
 * Copyright 2014-15 Intelix Pty Ltd
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

define(['react', 'core_mixin'], function (React, core_mixin) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [core_mixin],

        componentName: function () {
            return "app/content/gates/config/ReplayButton/" + this.props.ckey;
        },

        getInitialState: function () {
            return {connected: false}
        },

        handleClick: function (e) {
            if (confirm("Are you sure?")) {
                this.sendCommand(this.props.addr, this.props.ckey, "replay", {});
            }
        },

        render: function () {
            var self = this;

            var buttonClasses = this.cx({
                'disabled': (!self.state.connected || !self.props.enabled),
                'btn btn-default btn-xs': true
            });

            return <button type="button" ref="button" className={buttonClasses} onClick={this.handleClick}>Replay</button>;
        }
    });

});